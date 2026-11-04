# Deployment

Every deployment runs the same thing: the fat jar `build/libs/history-walk.jar`, on Java 25, with the
[environment variables](configuration.md) for its walk, and a Postgres database. On startup the server migrates the
database, checks its settings, and refuses to start if a required one is missing.

Whatever the host, a new deployment needs:

- [ ] A Postgres database of its own.
- [ ] The required variables: `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `OAUTH_CALLBACK_URL` (**`https`**), and
  `SESSION_SECRET` (its own).
- [ ] `SLIDES_VARIABLE_NAME` (`jermain` or `moses`) and `TEACHER_EMAILS`.
- [ ] Its callback added to the Google client's authorized redirect URIs (see [Google sign-in](google-sign-in.md)).
- [ ] A check after it starts: `/ping` answers `pong`, `/version` names the release, and a teacher can sign in.

## Building from the repository

A host that builds from the repository needs Java 25. It runs `./gradlew jar`, which writes
`build/libs/history-walk.jar`, and starts it with this command (on Railway, the service's **Settings → Deploy →
Custom Start Command**):

```bash
java -jar build/libs/history-walk.jar -port=$PORT
```

`-port=$PORT` makes the server listen on the port the host assigns; without it, the server listens on 8080. The
repository has no Procfile, so the start command must be set on the host. `build/libs` also holds two smaller jars, so
name `history-walk.jar` exactly rather than letting the host pick one. Its name has no version in it, so a release
never leaves the start command pointing at a file the build no longer writes.

A deployment behind Cloudflare needs one more check after each deploy, that Cloudflare serves the new release; see
[Releases and updates](releases.md#after-a-deploy).

## Railway

A Railway service runs the same jar or image with the same variables:

- Set the variables in the service's **Variables**. Give the server its database either with
  `DATABASE_URL = ${{Postgres.DATABASE_URL}}`, a reference to the Postgres service, which stays on Railway's private
  network, plus `DBMS_NAME` (`jermain` or `moses`) when the walk's database is not the one the URL names; or with
  `DBMS_URL` (`jdbc:pgsql://<host>:<port>/<database>`, note `pgsql`), `DBMS_USERNAME`, and `DBMS_PASSWORD`. When both
  are set, `DATABASE_URL` wins. Its connection is not encrypted, so don't point it at a database reached over the
  internet.
- Set `OAUTH_CALLBACK_URL` to `https://<service>.up.railway.app/auth/callback`, or the custom domain's, and add the
  same URI to the Google client. It must start with `https://`.
- Keep a copy of the service's variables outside the repository. `secrets/` is gitignored for this, such as
  `secrets/railway.env`.

!!! note "Building on the host"

    The repository's `Dockerfile` copies a jar built beforehand (`make docker-build` builds it first), and
    `.dockerignore` sends Docker nothing else. A host that builds that Dockerfile from a fresh clone finds no jar.
    Either run the image `make docker-push` publishes, or have the host build the jar with Gradle
    (`./gradlew jar`) and start it with `java -jar build/libs/history-walk.jar -port=$PORT`.

## Docker

The image holds a Java 25 JRE (Alpine) and the fat jar, nothing else, and runs it as an unprivileged user.

| Command | Does |
|---|---|
| `make docker-build` | Builds the jar, then the image `history-walk:<version>` and `:latest`. |
| `make docker-run` | Runs the image on port 8080 with `.env`, against the local `jermain` database from `make db` (`DOCKER_DB=moses` for the other; `DOCKER_PORT=8090` for another port). |
| `make docker-smoke` | Starts the image against a throwaway Postgres and checks that it migrates an empty database and answers `/ping`, `/version`, the page, and a refused API call. CI runs it on every pull request. |
| `make docker-push` | Builds the jar, then builds the image for `linux/amd64` and `linux/arm64` and pushes `pambrose/history-walk:<version>` and `:latest` to Docker Hub. It asks before pushing; `docker login` once first. `DOCKER_REPO` and `DOCKER_PLATFORMS` override the repository and the platforms. |

The image:

- listens on **8080** and has a health check that fetches `/ping` every 30 seconds;
- sizes its heap to 75% of the container's memory limit, and exits on running out of memory, so the host restarts it;
- takes its settings from the environment, like any other deployment: `docker run -e …` or the host's settings.

A push replaces what Docker Hub has under both tags. To keep the previous release's image, bump `version` in
`gradle.properties` first (see [Releases and updates](releases.md)).

## Moving a deployment

To move a walk to another host without losing anyone's progress:

1. Back up the database whole, including `public.flyway_schema_history` (see [Database](database.md#backups)).
2. Restore it on the new host, or point the new deployment at the same database.
3. Copy every variable, with a new `OAUTH_CALLBACK_URL` for the new address. Keeping the same `SESSION_SECRET` keeps
   everyone signed in; a new one signs them out.
4. Add the new callback to the Google client, start the deployment, and check `/version` and a sign-in.
5. Tell teachers the new address.
