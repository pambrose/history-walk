# History Walk

## Repos

There are 3 repos involved in this project:

* [history-walk](https://github.com/pambrose/history-walk)
* [history-walk-content](https://github.com/pambrose/history-walk-content)
* [history-walk-slides](https://github.com/pambrose/history-walk-slides)

The *history-walk* repo is duplicated in the client in *history-walk* and *history-walk-moses*. Only the *history-walk*
repo should be updated and the other should only pull from GitHub. Each is used to create
a Heroku app with different env vars One is called *jermain-walk* and the other is *moses-walk*.

The *history-walk-content* repo is used to hold the content for the slides. The content is edited there and then
copied to the *history-walk* repos.

The *history-walk-slides* repo is used to hold the core slide code. It is a dependancy of the other repos.

## Env Vars

### Local only
* SLIDES_LOCAL_FILENAME - The name of the local file to be used for the slides.

### Heroku
* SLIDES_REPO_TYPE
* SLIDES_REPO_OWNER
* SLIDES_REPO_NAME
* SLIDES_REPO_BRANCH
* SLIDES_REPO_PATH
* SLIDES_REPO_FILENAME
* SLIDES_VARIABLE_NAME - `jermain` for the Jermain Longuen walk; any other value (e.g. `moses`) selects the Moses walk.
* DISPLAY_CONSECUTIVE_CORRECT_DECISIONS

## Google sign-in

Users sign in with their Athenian Google account. Students (`NN...@athenian.org`, local part starting with two
digits) and the teachers listed in `TEACHER_EMAILS` are admitted; anyone else is refused.

### Google Cloud setup

1. In Google Cloud Console, create (or choose) a project in the athenian.org organization.
2. Configure the OAuth consent screen with user type **Internal**, so only athenian.org Workspace accounts can sign
   in. (External in Testing mode would block students.)
3. Create an OAuth client of type **Web application**, and copy its client id and client secret
   (`GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` below).
4. Add these authorized redirect URIs:
   * `https://<jermain-app>.herokuapp.com/auth/callback`
   * `https://<moses-app>.herokuapp.com/auth/callback`
   * `http://localhost:8080/auth/callback` (running the jar, or `make server` alone)
   * `http://localhost:3000/auth/callback` (with `make client`, whose dev server proxies `/auth/*`)
5. Set the environment variables below. The server refuses to start without `GOOGLE_CLIENT_ID`,
   `GOOGLE_CLIENT_SECRET`, and `OAUTH_CALLBACK_URL`.

| Variable | Purpose |
|---|---|
| `GOOGLE_CLIENT_ID` | Google OAuth client id |
| `GOOGLE_CLIENT_SECRET` | Google OAuth client secret |
| `OAUTH_CALLBACK_URL` | e.g. `https://<app>.herokuapp.com/auth/callback`; locally `http://localhost:8080/auth/callback` |
| `SESSION_SECRET` | Key for the session cookie and signed state; required in production |
| `SCHOOL_EMAIL_DOMAIN` | Defaults to `athenian.org` |
| `TEACHER_EMAILS` | Comma-separated teacher addresses; the only accounts with the teacher view. Empty means no teachers |
| `LOGIN_GREETING` | Title of the sign-in dialog; defaults to `History Walk` |

Generate `SESSION_SECRET` with:

```bash
openssl rand -hex 32
```

Use a distinct `SESSION_SECRET` for each Heroku app. Rotating it signs everyone out.

`./gradlew jvmTest` needs Docker running: `UserRepositoryTest` uses a Testcontainers Postgres.

Locally, `OAUTH_CALLBACK_URL` must match the port the browser uses: `http://localhost:3000/auth/callback` when
browsing the `make client` dev server, `http://localhost:8080/auth/callback` otherwise. A callback host of
`localhost`, `127.0.0.1`, or `::1` counts as local; a blank `SESSION_SECRET` then falls back to a dev-only secret
with a logged warning. Any other host is production and requires `SESSION_SECRET` (at least 16 characters).

## Gradle Tasks

### Running

* jsRun - Starts a webpack dev server on port 3000
* jvmRun - Starts a dev server on port 8080

### Packaging

* jsBrowserWebpack - Bundles the compiled js files into `build/dist`
* jsJar - Packages a standalone "web" frontend jar with all required files into `build/libs/*.jar`
* jvmJar - Packages a backend jar with compiled source files into `build/libs/*.jar`
* jar - Packages a "fat" jar with all backend sources and dependencies while also embedding frontend resources
  into `build/libs/*.jar`

## Docker

The image holds only a Java 25 JRE and the fat jar, which is built outside Docker:

```bash
make docker-build   # ./gradlew jar, then docker build -> history-walk:1.0.0 and history-walk:latest
make db             # the local Postgres the container connects to
make docker-run     # serves http://localhost:8080 using .env
```

`make docker-run` reads `.env` the way the shell does and passes each variable to the container, with two overrides:

* `DBMS_URL` points at the `DOCKER_DB` database (default `jermain`) through `host.docker.internal`, since localhost inside
  the container is the container itself.
* `OAUTH_CALLBACK_URL` becomes `http://localhost:<DOCKER_PORT>/auth/callback`, which must be an authorized redirect URI.

Use `DOCKER_PORT=8090` to run beside `make server`, and `DOCKER_DB=moses` (with
`SLIDES_VARIABLE_NAME=moses` in `.env`) for the Moses walk. The container's health check probes `/ping`.

## Heroku Deployment

* Add a Procfile
* Add config var `GRADLE_TASK = -Pprod=true jar`
* Add buildpack `heroku/gradle`

To determine the database URL:
```bash
heroku pg:credentials:url DATABASE -a app-name
```

### Pushing to Heroku from a branch

```bash
git push heroku Jan-2026:main
```

### Building the Database

The schema is managed by [Flyway](https://documentation.red-gate.com/flyway). Migrations live in
`src/jvmMain/resources/db/migration` and are applied automatically when the server starts, so every
Heroku deploy brings the database up to date. The initial schema is `V001__create_schema.sql`.

To change the schema, add a new migration with the next version number, e.g. `V002__add_column.sql`.
Never edit a migration that has already been applied: Flyway checksums applied migrations and will
refuse to start if one changes.

### Debug Deployment

* SLIDES_LOCAL_FILENAME=src/jvmMain/kotlin/Slides.kt
* SHOW_RESET_BUTTON=true
* ALLOW_SLIDE_ACCESS=true

There are two endpoints for see the student progress:

* /summary
* /reasons

An overview of the slides for debugging/reviewing is at /slides
(requires ALLOW_SLIDE_ACCESS=true)
