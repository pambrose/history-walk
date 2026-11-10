# History Walk

[![Tests](https://github.com/pambrose/history-walk/actions/workflows/tests.yml/badge.svg)](https://github.com/pambrose/history-walk/actions/workflows/tests.yml)
[![Lint](https://github.com/pambrose/history-walk/actions/workflows/lint.yml/badge.svg)](https://github.com/pambrose/history-walk/actions/workflows/lint.yml)
[![Docker Image](https://github.com/pambrose/history-walk/actions/workflows/docker.yml/badge.svg)](https://github.com/pambrose/history-walk/actions/workflows/docker.yml)
[![zizmor](https://github.com/pambrose/history-walk/actions/workflows/zizmor.yml/badge.svg)](https://github.com/pambrose/history-walk/actions/workflows/zizmor.yml)
[![Website](https://github.com/pambrose/history-walk/actions/workflows/website.yml/badge.svg)](https://github.com/pambrose/history-walk/actions/workflows/website.yml)
[![Release](https://img.shields.io/github/v/release/pambrose/history-walk)](https://github.com/pambrose/history-walk/releases)
[![Docs](https://img.shields.io/badge/docs-pambrose.github.io-blue)](https://pambrose.github.io/history-walk/)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.21-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Java](https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/25/)

A choose-your-own-path history scenario for Athenian students: a student makes the decisions of a person escaping
slavery and explains each one, and teachers follow their class's progress and reasons.

The guide for teachers and administrators is at <https://pambrose.github.io/history-walk/>.

## Local development

You need:

* **JDK 25**. Gradle runs on it whatever `JAVA_HOME` says (`gradle/gradle-daemon-jvm.properties`).
* **Docker**, for the local database and for the JVM tests: eight specs (`UserRepositoryTest`, `DbmsTxsTest`,
  `ContentServiceTest`, `PagesTest`, `AdminTest`, `UserResetTest`, `SignOutTest`, and `DbmsMigrateTest`) each start
  a Testcontainers Postgres.
* **Chrome**, for the client tests, which run in headless Chrome.
* **A Google OAuth client** with the localhost redirect URIs (see [Google sign-in](#google-sign-in)).

Put your settings in `.env` at the top of the repository (it is gitignored; see [Configuration](#configuration)):

```bash
GOOGLE_CLIENT_ID=….apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=…
OAUTH_CALLBACK_URL=http://localhost:3000/auth/callback
DBMS_URL=jdbc:postgresql://localhost:5432/jermain
TEACHER_EMAILS=you@athenian.org
```

Then:

```bash
make db        # Postgres 18 in Docker, with the jermain and moses databases
make server    # the server on :8080, rebuilt as you edit
make client    # the page on :3000, with hot reload, proxying the server's routes; open http://localhost:3000
make tests     # the JVM tests (Docker) and the client tests (Chrome)
make ci        # every CI gate: lint, zizmor, all tests, the Docker build, and its smoke test
```

`make` alone lists every target. [AGENTS.md](AGENTS.md) is the detailed guide to the code, for people and coding
agents: the architecture, sign-in, caching, the version check, every test, and the gotchas.

### Gradle tasks

The make targets wrap these:

* `jvmRun` - the server on port 8080 (`make server` runs it continuously)
* `jsBrowserDevelopmentRun` - the webpack dev server on port 3000 (`make client`)
* `jsBrowserProductionWebpack` - bundles the client into `build/kotlin-webpack/js/productionExecutable`;
  `jsBrowserDistribution` assembles it with the page's resources in `build/dist/js/productionExecutable`
* `jsJar` - packages a standalone "web" frontend jar into `build/libs`
* `jvmJar` - packages a backend jar of the compiled server into `build/libs`
* `jar` - packages the fat jar, `build/libs/history-walk.jar`: the server, its dependencies, and the bundled client.
  The name has no version, so the Dockerfile and a deployment's start command name the same file in every release.
  (`jar` is Gradle's prefix match for the Kilua RPC plugin's `jarWithJs` task.)

## Configuration

The server reads its settings from environment variables at startup. The
[configuration reference](https://pambrose.github.io/history-walk/admins/configuration/) has the details.

| Variable | Default | Purpose |
|---|---|---|
| `GOOGLE_CLIENT_ID` | required | The Google OAuth client id |
| `GOOGLE_CLIENT_SECRET` | required | That client's secret |
| `OAUTH_CALLBACK_URL` | required | `https://<deployment>/auth/callback`; locally `http://localhost:8080/auth/callback` |
| `SESSION_SECRET` | required in production | Key for the session cookie and the signed sign-in state, at least 16 characters |
| `SLIDES_VARIABLE_NAME` | `jermain` | The walk: `jermain` (the Jermain Longuen walk) or `moses`, in any case; any other value stops the server at startup |
| `TEACHER_EMAILS` | none | Comma-separated teacher addresses; the only accounts with the teacher view |
| `SCHOOL_EMAIL_DOMAIN` | `athenian.org` | The Google Workspace domain whose accounts may sign in |
| `LOGIN_GREETING` | `History Walk` | The title of the sign-in dialog |
| `SHOW_RESET_BUTTON` | `false` | `true` gives teachers a **reset** link for their own walk |
| `ALLOW_SLIDE_ACCESS` | `false` | `true` lets teachers open `/slides` and jump to any slide |
| `DISPLAY_CONSECUTIVE_CORRECT_DECISIONS` | `true` | `false` hides the *Consecutive Correct Decisions* line on every page |
| `DATABASE_URL` | none | A platform's Postgres URL, such as Railway's `${{Postgres.DATABASE_URL}}`; wins over the `DBMS_*` settings. Not encrypted unless its query string sets `sslmode`, so keep it on the platform's private network |
| `DBMS_NAME` | none | With `DATABASE_URL`: the walk's database on that server, when it isn't the one the URL names |
| `DBMS_QUERY_PATH` | none | With `DATABASE_URL`: the driver's parameters, such as `sslmode=require`, in place of the URL's query string |
| `DBMS_URL` | `jdbc:postgresql://localhost:5432/historywalk` | The database, as a PostgreSQL JDBC URL; not encrypted unless it sets `sslmode` |
| `DBMS_USERNAME` | `postgres` | The database user |
| `DBMS_PASSWORD` | `docker` | That user's password |
| `CLIENT_VERSION_CHECK_INTERVAL_SECS` | `60` | How often an open page asks whether its version is current |
| `CLIENT_RELOAD_WARNING_INTERVAL_SECS` | `60` | How long an out-of-date page shows its reload dialog before it reloads itself |

Generate `SESSION_SECRET` with `openssl rand -hex 32`, and use a distinct one for each deployment. Rotating it signs
everyone out. A callback host of `localhost`, `127.0.0.1`, or `::1` counts as local: there a blank `SESSION_SECRET` falls
back to a dev-only secret with a logged warning. Any other host is production and requires `SESSION_SECRET`.

## Google sign-in

Users sign in with their Athenian Google account. Students (`NN...@athenian.org`, local part starting with two
digits) and the teachers listed in `TEACHER_EMAILS` are admitted; anyone else is refused. A sign-in lasts 12 hours, and
signing out ends that account's sessions in every browser, not only the one used.

### Google Cloud setup

1. In Google Cloud Console, create (or choose) a project in the athenian.org organization.
2. Configure the OAuth consent screen with user type **Internal**, so only athenian.org Workspace accounts can sign
   in. (External in Testing mode would block students.)
3. Create an OAuth client of type **Web application**, and copy its client id and client secret into
   `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET`.
4. Add these authorized redirect URIs:
   * `https://<jermain-deployment>/auth/callback`
   * `https://<moses-deployment>/auth/callback`
   * `http://localhost:8080/auth/callback` (running the jar, `make server` alone, or `make docker-run`)
   * `http://localhost:3000/auth/callback` (with `make client`, whose dev server proxies `/auth/*`)

   A deployed callback must be `https`: Google accepts `http` only for `localhost`.
5. Set `OAUTH_CALLBACK_URL` on each deployment to its redirect URI. The server refuses to start without
   `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, and `OAUTH_CALLBACK_URL`.

Locally, `OAUTH_CALLBACK_URL` must match the port the browser uses: `http://localhost:3000/auth/callback` when browsing
the `make client` dev server, `http://localhost:8080/auth/callback` otherwise.

## Docker

The image holds only a Java 25 JRE and the fat jar, which is built outside Docker:

```bash
make docker-build   # ./gradlew jar, then docker build -> history-walk:<version> and history-walk:latest
make db             # the local Postgres the container connects to
make docker-run     # serves http://localhost:8080 using .env
```

`make docker-run` reads `.env` the way the shell does and passes each variable to the container, with two overrides:

* `DBMS_URL` points at the `DOCKER_DB` database (default `jermain`) through `host.docker.internal`, since localhost inside
  the container is the container itself.
* `OAUTH_CALLBACK_URL` becomes `http://localhost:<DOCKER_PORT>/auth/callback`, which must be an authorized redirect URI.

Use `DOCKER_PORT=8090` to run beside `make server`, and `DOCKER_DB=moses` (with
`SLIDES_VARIABLE_NAME=moses` in `.env`) for the Moses walk. The container's health check probes `/ping`.

`make docker-push` publishes the image to Docker Hub as `pambrose/history-walk:<version>` and `:latest`, built for
`linux/amd64` and `linux/arm64` with `docker buildx`. Run `docker login` once first. It asks before pushing, and
`DOCKER_REPO` and `DOCKER_PLATFORMS` override the repository and the platforms.

## Deployment

Each walk is its own deployment of the fat jar, on Java 25, with its own Postgres database and environment variables.
A host that builds from the repository runs `./gradlew jar` and starts the jar with this start command (on Railway,
the service's custom start command):

```bash
java -jar build/libs/history-walk.jar -port=$PORT
```

A host that runs containers can run the image `make docker-push` publishes; the two Railway services do. The
[deployment guide](https://pambrose.github.io/history-walk/admins/deployment/) covers both.

### Releasing

`version` in `gradle.properties` is the release. Add its sections to `CHANGELOG.md` and `RELEASE_NOTES.md`, merge to
`master`, and once CI passes:

```bash
make release-check   # lists every reason this checkout can't be released yet; publishes nothing
make release         # every CI gate, then asks once, pushes the image to Docker Hub, creates the GitHub release
```

The GitHub release is tagged with the version (`X.Y.Z`), titled with a `v` (`vX.Y.Z`), and takes its notes from the
version's section of `RELEASE_NOTES.md`. Then `make railway-redeploy` deploys both walks on Railway from the new image,
and waits until each reports the new version.

`make log` prints the last 100 lines of each walk's Railway log (`SERVICE=Moses` for one walk, `LOG_LINES=500` for
more). `make log-jermain` and `make log-moses` follow one walk's log like `tail -f`: its last 10 lines, then each new
line within a few seconds, until Ctrl-C.

### Behind Cloudflare

The page, `main.bundle.js`, and the images go out with `Cache-Control: no-cache, private` and an ETag, so Cloudflare
keeps no copy and a browser checks with the server on each page load, getting a 304 until a release changes the file.
After a deploy, check what each side serves:

```bash
make served-version PUBLIC_URL=https://<cloudflare-address> ORIGIN_URL=https://<origin-address>
```

It fails if Cloudflare serves an older bundle than the origin (purge `main.bundle.js` in Cloudflare) or caches the
bundle. Do not add a Cloudflare cache rule that overrides the origin's `Cache-Control`.

## Database

The schema is managed by [Flyway](https://documentation.red-gate.com/flyway). Migrations live in
`src/jvmMain/resources/db/migration` and are applied automatically when the server starts, so every deploy brings the
database up to date. The initial schema is `V001__create_schema.sql`; `V002__session_version.sql` adds the per-user
session version that signing out increments, and `V003__teachers_see_student_view.sql` the admin page's student-view
switch.

To change the schema, add a new migration with the next version number, e.g. `V004__add_column.sql`. Never edit a
migration that has already been applied: Flyway checksums applied migrations and will refuse to start if one changes.

Flyway records the migrations it has applied in `public.flyway_schema_history`, outside the `history` schema that holds
the users and their choices. `V001` begins by dropping the `history` schema, so if `history` holds tables while that
table is missing or empty, the server refuses to start rather than run `V001` again, and its error names the fix. A
backup or restore must therefore include `public.flyway_schema_history`: a backup of the whole database is fine; a dump
of the `history` schema alone is not.

## Teacher pages

These are for teachers only: a student is refused, and someone not signed in is sent to sign in first.

* `/summary` - the class summary: each student's decisions, success, and current slide.
* `/reasons` - every reason every student gave.
* `/admin` - switches every teacher to the student view and back, so teachers can walk as their class will before
  giving it the app. While teachers see the student view, their moves count in `/summary` and `/reasons` like a
  student's, and they stay there after switching back until the admin page's **Clear teachers' trial walks** deletes
  every teacher's walk.
* `/slides` - an overview of every slide, for reviewing the walk, with a jump to any of them (requires
  `ALLOW_SLIDE_ACCESS=true`).
* `/threaddump` - a dump of every server thread's stack. It briefly pauses the JVM while it runs.

With `SHOW_RESET_BUTTON=true`, a teacher's page also has a **reset** link that deletes the teacher's own walk, after
asking.

## License

History Walk is licensed under the [Apache License, Version 2.0](LICENSE).
