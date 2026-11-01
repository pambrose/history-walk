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
make tests              # ./gradlew jvmTest --rerun (needs Docker: the database specs use Testcontainers)
make coverage           # the tests plus Kover reports (build/reports/kover/html/index.html)
./gradlew jvmTest --tests 'com.pambrose.historywalk.auth.AuthRoutesTest'   # one spec
make lint               # kotlinter (./gradlew lintKotlin); make format fixes what it can
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
`MarkdownParser`. `Main.kt` picks the deck: `SLIDES_VARIABLE_NAME == "jermain"` selects Jermain, anything else
Moses. (`HistoryWalkServer.loadSlides()` and the `SLIDES_REPO_*` / `SLIDES_LOCAL_FILENAME` vars are from when decks were
fetched from the history-walk-content repo at runtime; that call is commented out.) Slide images are jar resources under
`src/jvmMain/resources/static/images`, referenced as `/static/images/<name>`.

A go-back choice, `goBack(-n)`, returns the student to the slide's n-th ancestor (-1 is its parent);
`SlideData.goBackTarget` (commonMain) works it out for the client, and `SlideContentTest` checks that every Jermain
go-back, each offered as trying another building, lands on the town the student is in.

A slide's identity is its `pathName`, the chain of titles from the root (`/Title/Sub title`). Progress is stored by
path name (`users.last_path_name`, `user_choices.from_path_name`/`to_path_name`), so renaming a slide title or moving a
slide orphans saved progress, and `findSlideByPathName` silently falls back to the root slide.

**Database.** `Dbms.migrate` runs Flyway (`src/jvmMain/resources/db/migration`) before `Database.connect` on every
startup, so each deploy migrates. Never edit an applied migration (Flyway checksums them); add `V002__...sql`. Note
`V001` begins with `drop schema if exists history cascade`. Tables are Exposed objects in `dbms/DbmsTables.kt`; queries are
in `dbms/DbmsTxs.kt` and `User.kt`.

**Dev proxy.** In `make client`, the browser talks to :3000; `webpack.config.d/proxy.js` forwards `/auth`, `/rpc*`,
`/static`, and the teacher/reset pages to :8080. A new server route that the browser loads directly must be added there.
The files in `webpack.config.d/` are concatenated into the generated webpack config; the app does not use jQuery (an old
`ProvidePlugin` for it broke Bootstrap under jQuery 4 and was removed).

**Cloudflare caching.** The apps sit behind Cloudflare, and `main.bundle.js` has the same name in every release, so a
release reaches a browser at its next page load only because of these; do not drop any of them:
- `Routes.staticContent` serves the page shell (`/`, the jar's `assets/index.html`), the bundle, and the `/static`
  images with `Cache-Control: no-cache, private` and a SHA-256 ETag (`revalidatedEachTime`). Kilua's own static
  handling is off (`initRpc(initStaticResources = false)`): it sent no Cache-Control, so Cloudflare kept the bundle for
  hours and told browsers to keep it too, and it answered every unknown path with the page shell.
- `ConditionalHeaders` (`Installs.installConditionalHeaders`) turns a matching `If-None-Match` into a 304 with no body.
- `AutoHeadResponse` (`Installs.installAutoHeadResponse`) answers HEAD, which `curl -sI` sends, with a GET's headers;
  without it every HEAD is a 404, which Cloudflare can pass on in place of the bundle's headers.

Cloudflare keeps no copy of a response marked `private` (`cf-cache-status: BYPASS`) and passes the header on unchanged;
a Cloudflare cache rule that ignores the origin's headers would bring stale bundles back. A change to these headers does
not clear a copy Cloudflare already holds: after a deploy, run `make served-version PUBLIC_URL=... ORIGIN_URL=...`,
which reads the version line `App.start()` logs from the bundle as Heroku and as Cloudflare serve it, and purge
`main.bundle.js` in Cloudflare if they differ. `StaticContentCachingTest` builds its own routes from `staticContent()`,
so it would not notice `appRoutes` dropping that call.

**Version check.** A page left open across a release keeps its old bundle, so `ClientVersionCheck` (started from
`App.start()`) sends its `BuildConfig` versions to `IContentService.versionCheck` every
`CLIENT_VERSION_CHECK_INTERVAL_SECS` (60). `ServerVersionCheck` compares only `apiVersion` (gradle.properties): on a
mismatch the page shows a dialog with a reload link, then, `CLIENT_RELOAD_WARNING_INTERVAL_SECS` (60) after the first
warning, a countdown toast before it reloads itself. Increment `apiVersion` only when the RPC interfaces change, or when
a release must replace pages already open; a release that leaves it alone reaches each page at its next load. The check
goes around `withAuth`, so a signed-out page is left to the sign-in dialog.

## Tests

JVM tests only (`src/jvmTest`), Kotest `StringSpec` + MockK + Ktor test host. Route tests call `appRoutes(...)` with
injected `findEmail`/`teacherEmails` lambdas instead of a database. The database specs (`UserRepositoryTest`,
`DbmsTxsTest`, `ContentServiceTest`) each start their own `TestPostgres`, a Testcontainers `postgres:18` with the real
Flyway migration applied, and share the small deck and helpers in `TestWalk.kt`. `ContentServiceTest` mocks
`ApplicationCall.userUuid` (a `mockkStatic` of `UserSessionKt`) to drive the RPC service as a signed-in user.
`SlideContentTest` walks both real decks from the root and fails on a path name that does not find its slide, a slide
shared between two parents, a go-back past the root, a missing image, or a success slide whose title differs from the
deck's (the summary page counts successes by that title).

## Gotchas

- IntelliJ has twice auto-imported JDK-internal classes (`sun.jvmstat...`, `sun.jvm.hotspot.HelloWorld.e`) into Kotlin
  files, which breaks the build; delete such imports.
- Heroku: each app's env vars are listed in `heroku.md` and the README's "Google sign-in" section; the Procfile runs
  `java -jar build/libs/history-walk-1.0.0.jar -port=$PORT`, so the Docker image and Heroku both run the same fat jar.
- `docs/superpowers/` holds the Google sign-in design spec and implementation plan; they predate folding `V002` into `V001`.
