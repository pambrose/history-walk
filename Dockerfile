# The fat jar is built outside Docker (make docker-build runs ./gradlew jar first), so the image holds only a JRE and that jar.
# The base is pinned to its multi-arch (amd64 and arm64) index digest, so a commit always builds on the same JRE; the
# tag is kept for readers. Dependabot (.github/dependabot.yml) opens a PR with the new digest when the tag moves.
FROM eclipse-temurin:25-jre-alpine@sha256:69c2a26e9894016e274c880fa542956d005b3755f84db765145185e87405bd89
LABEL maintainer="Paul Ambrose <pambrose@mac.com>"

# The base image predates Alpine 3.24's zlib 1.4-r0, which fixes CVE-2026-4738 (CRITICAL to Docker Scout, MEDIUM to
# Trivy) in the 1.3.2-r1 it ships. The JRE has a zlib of its own, so this one serves the font libraries and apk. Upgrade
# only that package, and fail the build rather than ship an older one. Drop this once a new base digest carries the fix.
RUN apk add --no-cache --upgrade 'zlib>=1.4-r0'

# Run as an unprivileged user rather than root, even inside the container. The user owns nothing it runs: /app and the
# jar stay root's, readable by all, so code that got to run in the server could not rewrite the jar and outlive a restart.
# The server writes nothing to disk; its log goes to stdout.
ENV APPLICATION_USER=historywalk
RUN adduser --disabled-password --gecos '' $APPLICATION_USER && mkdir /app

# The version comes from gradle.properties and the revision is the commit (make docker-build and make release pass both);
# they only label the image, and make release-github checks the revision before it tags a release. The jar's file name
# leaves the version out (build.gradle.kts), so this COPY and a deployment's start command name the same file in every
# release.
ARG APP_VERSION
ARG APP_REVISION
LABEL org.opencontainers.image.version=$APP_VERSION
LABEL org.opencontainers.image.revision=$APP_REVISION
# The jar carries the compiled JS client, logback.xml, the Flyway migrations, and the slide images, so nothing else is copied.
COPY ./build/libs/history-walk.jar /app/history-walk.jar

USER $APPLICATION_USER

WORKDIR /app

EXPOSE 8080

# /ping answers without touching the database or a session, so it checks only that the server is up.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -q -O /dev/null http://localhost:8080/ping || exit 1

# Size the heap from the container's memory limit, and exit on an OutOfMemoryError so the container is restarted.
ENTRYPOINT ["java", "-server", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/history-walk.jar"]
