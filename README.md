# History Walk

## Env Vars

* SLIDES_VARIABLE_NAME - `jermain` for the Jermain Longuen walk (the default when unset), or `moses` for the Moses walk,
  in any case. Any other value stops the server at startup with an error naming it.
* DISPLAY_CONSECUTIVE_CORRECT_DECISIONS
* CLIENT_VERSION_CHECK_INTERVAL_SECS - How often an open page asks whether its version is current (default 60).
* CLIENT_RELOAD_WARNING_INTERVAL_SECS - How long an out-of-date page shows its reload dialog before it reloads itself
  (default 60).

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

A sign-in lasts 12 hours. Signing out ends that account's sessions in every browser, not only the one used.

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
  into `build/libs/history-walk.jar`. The name has no version, so the Procfile and the Dockerfile name the same file in
  every release; `make procfile-check`, run after a build, fails if the Procfile names a file the build did not write.
  (`jar` is Gradle's prefix match for the Kilua RPC plugin's `jarWithJs` task.)

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

### Behind Cloudflare

The apps are served through Cloudflare. The page, `main.bundle.js`, and the images go out with
`Cache-Control: no-cache, private` and an ETag, so Cloudflare keeps no copy and a browser checks with the server on each
page load, getting a 304 until a release changes the file. After a deploy, check what each side serves:

```bash
make served-version PUBLIC_URL=https://<cloudflare-address> ORIGIN_URL=https://<app>.herokuapp.com
```

It fails if Cloudflare serves an older bundle than Heroku (purge `main.bundle.js` in Cloudflare) or caches the bundle.
Do not add a Cloudflare cache rule that overrides the origin's `Cache-Control`.

### Pushing to Heroku from a branch

```bash
git push heroku Jan-2026:main
```

### Building the Database

The schema is managed by [Flyway](https://documentation.red-gate.com/flyway). Migrations live in
`src/jvmMain/resources/db/migration` and are applied automatically when the server starts, so every
Heroku deploy brings the database up to date. The initial schema is `V001__create_schema.sql`;
`V002__session_version.sql` adds the per-user session version that signing out increments.

To change the schema, add a new migration with the next version number, e.g. `V003__add_column.sql`.
Never edit a migration that has already been applied: Flyway checksums applied migrations and will
refuse to start if one changes.

Flyway records the migrations it has applied in `public.flyway_schema_history`, outside the `history`
schema that holds the users and their choices. `V001` begins by dropping the `history` schema, so if
`history` holds tables while that table is missing or empty, the server refuses to start rather than
run `V001` again, and its error names the fix. A backup or restore must therefore include
`public.flyway_schema_history`: `heroku pg:backups:restore` and `heroku pg:copy` take whole databases,
which is fine; a dump of the `history` schema alone is not.

### Debug Deployment

* SHOW_RESET_BUTTON=true
* ALLOW_SLIDE_ACCESS=true

There are two endpoints for see the student progress:

* /summary
* /reasons

An overview of the slides for debugging/reviewing is at /slides
(requires ALLOW_SLIDE_ACCESS=true)

A dump of every server thread's stack is at /threaddump. It pauses the JVM while it runs.

All of these are for teachers only.
