# The fat jar is built outside Docker (make docker-build runs ./gradlew jar first), so the image holds only a JRE and that jar.
FROM eclipse-temurin:25-jre-alpine
LABEL maintainer="Paul Ambrose <pambrose@mac.com>"

# Run as an unprivileged user rather than root, even inside the container.
ENV APPLICATION_USER=historywalk
RUN adduser --disabled-password --gecos '' $APPLICATION_USER && mkdir /app && chown -R $APPLICATION_USER /app
USER $APPLICATION_USER

# The version comes from gradle.properties (make docker-build passes it) and only labels the image: the jar's file name
# leaves the version out (build.gradle.kts), so this COPY and the Procfile name the same file in every release.
ARG APP_VERSION
LABEL org.opencontainers.image.version=$APP_VERSION
# The jar carries the compiled JS client, logback.xml, the Flyway migrations, and the slide images, so nothing else is copied.
COPY --chown=$APPLICATION_USER ./build/libs/history-walk.jar /app/history-walk.jar

WORKDIR /app

EXPOSE 8080

# /ping answers without touching the database or a session, so it checks only that the server is up.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -q -O /dev/null http://localhost:8080/ping || exit 1

# Size the heap from the container's memory limit, and exit on an OutOfMemoryError so the container is restarted.
ENTRYPOINT ["java", "-server", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/history-walk.jar"]
