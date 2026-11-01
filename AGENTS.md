# AGENTS.md

This file provides guidance to AI coding agents working with code in this repository.

## What this is

History Walk is a choose-your-own-path history scenario for Athenian students. It is a Kotlin Multiplatform app: a Ktor
server (`jvmMain`) and a KVision single-page client (`jsMain`) that talk over Kilua RPC, with Postgres behind Exposed and
Flyway. The same repo is deployed as two Heroku apps (jermain-walk and moses-walk) that differ only in env vars.

## Commands

The build needs JDK 25 (`gradle/gradle-daemon-jvm.properties` pins the daemon toolchain; Heroku reads `system.properties`).
`make` with no target prints the help. The project `group` and `version` live in `gradle.properties`.

```bash
make db                 # Postgres 18 in Docker (container history-walk) with the jermain and moses databases
make reset-jermain      # drop and recreate one database (reset-moses for the other); Flyway rebuilds the schema on next start
make server             # Ktor on :8080, continuous rebuild (./gradlew jvmRun -t)
make client             # webpack dev server on :3000 with hot reload, proxying server routes to :8080
make tests              # ./gradlew jvmTest --rerun (needs Docker: UserRepositoryTest uses Testcontainers)
./gradlew jvmTest --tests 'com.github.pambrose.auth.AuthRoutesTest'   # one spec
./gradlew lintKotlin    # kotlinter; formatKotlin to fix
make build              # clean + fat jar at build/libs/history-walk-<version>.jar (server + bundled JS client)
make docker-build       # fat jar, then image history-walk:<version>; make docker-run runs it with .env against make db
```

Local settings and secrets live in `.env` (gitignored, loaded by the IntelliJ run configs and `make docker-run`). The
server refuses to start without `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, and `OAUTH_CALLBACK_URL`. The DB defaults in
`Property.kt` point at a database named `historywalk`, so set `DBMS_URL` (e.g. `jdbc:pgsql://localhost:5432/jermain`).
The app uses the pgjdbc-ng driver (`jdbc:pgsql:` URLs); the tests use the standard `org.postgresql` driver.

## Architecture

**RPC boundary.** `IContentService` (`commonMain/Services.kt`, `@RpcService`) is the whole client/server API. KSP
generates `ContentServiceManager`; `Main.kt` registers `ContentService(call)` per request, so the implementation reads
the signed-in user from the `ApplicationCall` (`call.userUuid`), never from a client-supplied id. DTOs are in
`commonMain/Dao.kt`. The client calls go through `jsMain/Rpc.kt`, which wraps each in `withAuth`.

**Sign-in (Google OAuth, `jvmMain/auth/`).** Only `athenian.org` accounts (`SCHOOL_EMAIL_DOMAIN`) are admitted. The role
rule (`Roles.kt`): an address in `TEACHER_EMAILS` is a teacher, else a local part starting with two digits is a student,
else refused. The role is recomputed from the live `TEACHER_EMAILS` on every request, not taken from the stored row.
- `AuthRoutes.kt` runs `/auth/login` and `/auth/callback` with a signed stateless `state` plus an `oauth_state` cookie and
  PKCE; `SignInDecision.kt` decides admit/refuse; users are upserted into `history.users` (emails stored lowercase).
- The session is an encrypted, signed cookie (`HistoryWalkAuth`, `__Host-` prefixed in production) validated against the
  users table on every request (`SessionValidation.kt`). `SESSION_SECRET` is required unless the callback host is local.
- `Routes.appRoutes` is the access map: RPC inside `signedInApi` (no session → 401), teacher pages (`/summary`, `/reasons`,
  `/slides`) inside `teachersOnly` (→ 403 for students), `/ping`, `/version`, and `/static/*` open to all.
- On the client, Kilua turns only a 401 into `SecurityException`; KVision's `SecurityMgr.withAuth` then calls
  `Security.login()`, which shows `SignInDialog` once and parks forever (see the comment on `LoginPrompt` before changing it).

An explicit anti-goal: a student must never see another student's data or progress. Teacher pages read the
`history.user_*` views, which filter to `role = 'student'` so teachers' test walks never show up.

**Slides.** Decks are Kotlin DSL values in `jvmMain/content/` (`jermainLonguenSlides`, `mosesSlides`) built with
`slides/SlideDeck.slideDeck { slide(...) { choice(...) } }`; content is HTML/markdown in raw strings, rendered through
`MarkdownParser`. `Main.kt` picks the deck: `SLIDES_VARIABLE_NAME == "jermainLonguenSlides"` selects Jermain, anything else
Moses. (`HistoryWalkServer.loadSlides()` and the `SLIDES_REPO_*` / `SLIDES_LOCAL_FILENAME` vars are from when decks were
fetched from the history-walk-content repo at runtime; that call is commented out.) Slide images are jar resources under
`src/jvmMain/resources/static/images`, referenced as `/static/images/<name>`.

A slide's identity is its `pathName`, the chain of titles from the root (`/Title/Sub title`). Progress is stored by
path name (`users.last_path_name`, `user_choices.from_path_name`/`to_path_name`), so renaming a slide title or moving a
slide orphans saved progress, and `findSlideByPathName` silently falls back to the root slide.

**Database.** `Dbms.migrate` runs Flyway (`src/jvmMain/resources/db/migration`) before `Database.connect` on every
startup, so each deploy migrates. Never edit an applied migration (Flyway checksums them); add `V002__...sql`. Note
`V001` begins with `drop schema if exists history cascade`. Tables are Exposed objects in `DbmsTables.kt`; queries are
in `DbmsTxs.kt` and `User.kt`.

**Dev proxy.** In `make client`, the browser talks to :3000; `webpack.config.d/proxy.js` forwards `/auth`, `/rpc*`,
`/static`, and the teacher/reset pages to :8080. A new server route that the browser loads directly must be added there.
The files in `webpack.config.d/` are concatenated into the generated webpack config; the app does not use jQuery (an old
`ProvidePlugin` for it broke Bootstrap under jQuery 4 and was removed).

## Tests

JVM tests only (`src/jvmTest`), Kotest `StringSpec` + MockK + Ktor test host. Route tests call `appRoutes(...)` with
injected `findEmail`/`teacherEmails` lambdas instead of a database; `UserRepositoryTest` runs the real Flyway migration
against a Testcontainers `postgres:18`.

## Gotchas

- IntelliJ has twice auto-imported JDK-internal classes (`sun.jvmstat...`, `sun.jvm.hotspot.HelloWorld.e`) into Kotlin
  files, which breaks the build; delete such imports.
- Heroku: each app's env vars are listed in `heroku.md` and the README's "Google sign-in" section; the Procfile runs
  `java -jar build/libs/history-walk-1.0.0.jar -port=$PORT`, so the Docker image and Heroku both run the same fat jar.
- `docs/superpowers/` holds the Google sign-in design spec and implementation plan; they predate folding `V002` into `V001`.
