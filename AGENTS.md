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
make zizmor             # audit .github (workflows, dependabot.yml) as the zizmor workflow does
make ci                 # every CI gate: lint, zizmor, tests with coverage, docker-build, procfile-check, docker-smoke
make build              # clean + fat jar at build/libs/history-walk.jar (server + bundled JS client; no version)
make procfile-check     # after a build: fails unless the jar the Procfile runs is in build/libs
make docker-build       # fat jar, then image history-walk:<version>; make docker-run runs it with .env against make db
make docker-smoke       # start the image against a throwaway Postgres and check /ping, /version, headers, the 401
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

**Moves are checked on the server.** `ContentService` takes the deck (`masterSlides`) and the user's stored slide as the
truth, not what the client sends:
- `makeChoice` and `provideReason` must start from the user's current slide and name one of its forward choices. The
  stored title, choice text, and `deadEnd` come from the deck, and `advance` is derived as the client does
  (`choices.size == 1`, go-backs counted).
- `goBackInTime` must name an ancestor, the current slide, or `/` (the client's "back to the start").
- A reason must pass `ReasonRule` (`commonMain`, at least 5 words, at most 2000 characters); the reason dialog uses the
  same rule.
- A repeat of a move just made (a double-click, a second dialog, a retried transaction) is accepted and changes nothing:
  `provideReason` inserts with `ON CONFLICT DO NOTHING`, so the first reason wins.
- Anything else is refused with a Kilua `ServiceException`, whose message reaches the browser.

The RPC signatures still carry the fields the server now ignores, so changing them needs an `apiVersion` bump.

**Client errors.** `AppScope` has a `SupervisorJob`, and the version check runs in a scope of its own, so a failed call
no longer cancels every later click. Clicks on choices and go-backs go through `onClickWhenIdle` (`MainPanel.kt`):
- A `busy` flag disables those buttons until the request finishes.
- A failure shows a toast (`ClientUtils.orErrorToast`), and a server refusal shows its own message and reloads the
  student's real slide.
- Only a `SecurityException` (401) goes to `Security.withAuth` and the sign-in dialog. Until a first call succeeds,
  KVision's `SecurityMgr.withAuth` takes any exception for "sign in needed", so `ClientUtils.withAuth` carries every
  other failure past it in a `Result` and rethrows it to the caller: a network error at start-up shows the
  `PAGE_NOT_LOADED` toast, not the sign-in dialog.

**Sign-in (Google OAuth, `jvmMain/auth/`).** Only `athenian.org` accounts (`SCHOOL_EMAIL_DOMAIN`) are admitted. The role
rule (`Roles.kt`): an address in `TEACHER_EMAILS` is a teacher, else a local part starting with two digits is a student,
else refused. The role is recomputed from the live `TEACHER_EMAILS` on every request, not taken from the stored row.
- `AuthRoutes.kt` runs `/auth/login` and `/auth/callback` with a signed stateless `state` and PKCE, storing nothing on
  the server. The `oauth_state` cookie (`OAuthState.kt`) holds a random secret plus an HMAC binding it to the state, and
  the PKCE verifier is derived from that secret (`Pkce.kt`), so a stolen callback URL is useless without the browser's
  cookie. The provider's `verifyState` hook (`ConfigureAuth.kt`) refuses a callback whose cookie is missing or not bound
  to its state before the code is redeemed; `SignInDecision.kt` decides admit/refuse; users are upserted into
  `history.users` (emails stored lowercase).
- The session is an encrypted, signed cookie (`HistoryWalkAuth`, `__Host-` prefixed in production) validated against the
  users table on every request (`SessionValidation.kt`). `SESSION_SECRET` is required unless the callback host is local.
- A sign-in lasts 12 hours (`UserSession.LIFETIME`, checked on the server as well as the cookie's max age). The cookie
  carries the user's `users.session_version`; `POST /logout` increments it (`signOutEverywhere`), which ends the user's
  sessions in every browser, copied cookies included. `SessionValidation` reads the address and the version in one
  query (`User.sessionUserFor`). `installUserSessions` takes that lookup and the increment (`findUser`, `endSessions`),
  so route tests can run without a database.
- `Routes.appRoutes` is the access map: RPC inside `signedInApi` (no session → 401), teacher pages (`/summary`, `/reasons`,
  `/slides`, `/threaddump`) inside `teachersOnly` (→ 403 for students), `/ping`, `/version`, and `/static/*` open to all.
- `/userreset` deletes the caller's own choices and is a POST, as `/logout` is (the client posts with
  `ClientUtils.postTo`); a GET or HEAD of either only redirects to `/`, since any link, image, or prefetch can send one.
- Sign-in, sign-out, `/userreset`, and the teacher pages sit inside `noStore` (`RouteGuards.kt`), which sends
  `Cache-Control: no-store, private` on every response there, the Google redirect and the guards' 403 included, so after
  a sign-out on a shared computer Back cannot show class data from a cache and Cloudflare stores none of it.
- On the client, Kilua turns only a 401 into `SecurityException`; KVision's `SecurityMgr.withAuth` then calls
  `Security.login()`, which shows `SignInDialog` once and parks forever (see the comment on `LoginPrompt` before changing it).
- `App.start()` reloads a page the browser restores from its back/forward cache (`pageshow` with `persisted`), so Back
  after a sign-out shows the sign-in dialog rather than the previous user's walk.

An explicit anti-goal: a student must never see another student's data or progress. Teacher pages read the
`history.user_*` views, which filter to `role = 'student'` so teachers' test walks never show up.

**Slides.** Decks are Kotlin DSL values in `jvmMain/content/` (`jermainLonguenSlides`, `mosesSlides`) built with
`slides/SlideDeck.slideDeck { slide(...) { choice(...) } }`; content is HTML/markdown in raw strings, rendered through
`MarkdownParser`. `Main.kt` picks the deck with `slideDeckFor` (`content/SlideDecks.kt`): `SLIDES_VARIABLE_NAME` is
`jermain` (the default when unset or blank) or `moses`, trimmed and in any case; any other value stops the server at
startup, naming the value. The decks are compiled into the jar: nothing fetches or evaluates slide code at runtime, so
a content change ships with a release. Slide images are jar resources under `src/jvmMain/resources/static/images`,
referenced as `/static/images/<name>`.

A go-back choice, `goBack(-n)`, returns the student to the slide's n-th ancestor (-1 is its parent);
`SlideData.goBackTarget` (commonMain) works it out for the client, and `SlideContentTest` checks that every Jermain
go-back, each offered as trying another building, lands on the town the student is in.

A slide's identity is its `pathName`, the chain of titles from the root (`/Title/Sub title`). Progress is stored by
path name (`users.last_path_name`, `user_choices.from_path_name`/`to_path_name`), so renaming a slide title or moving a
slide orphans saved progress, and `findSlideByPathName` silently falls back to the root slide. `SlidePathsTest` pins
every path name a student can reach in each deck to a snapshot, `src/jvmTest/resources/slide-paths/<deck>.txt`, so such
an edit fails CI, listing the paths that disappeared (each orphans the students saved there) and those that appeared. A
deliberate rename needs either a migration that moves the stored path names or acceptance that the affected students
restart, together with the snapshot rewritten and committed with the deck change:
`./gradlew jvmTest --tests 'com.pambrose.historywalk.content.SlidePathsTest' -PupdateSlidePaths=true`.

**Database.** `Dbms.migrate` runs Flyway (`src/jvmMain/resources/db/migration`) before `Database.connect` on every
startup, so each deploy migrates. Never edit an applied migration (Flyway checksums them); add the next `V00N__...sql`
(`V002` added `users.session_version`). Note `V001` begins with `drop schema if exists history cascade`. Tables are
Exposed objects in `dbms/DbmsTables.kt`; queries are in `dbms/DbmsTxs.kt` and `User.kt`.

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
so it would not notice `appRoutes` dropping that call; `NoStoreCachingTest` checks the shell, the bundle, and an image
through `appRoutes`, and that the per-user routes (sign-in and sign-out, the reset, the teacher pages) go out
`no-store, private` instead (see `noStore` under Sign-in).

**Version check.** A page left open across a release keeps its old bundle, so `ClientVersionCheck` (started from
`App.start()`) sends its `BuildConfig` versions to `IContentService.versionCheck` every
`CLIENT_VERSION_CHECK_INTERVAL_SECS` (60). `ServerVersionCheck` compares only `apiVersion` (gradle.properties): on a
mismatch the page shows a dialog with a reload link, then, `CLIENT_RELOAD_WARNING_INTERVAL_SECS` (60) after the first
warning, a countdown toast before it reloads itself. Increment `apiVersion` only when the RPC interfaces change, or when
a release must replace pages already open; a release that leaves it alone reaches each page at its next load. The check
goes around `withAuth`, so a signed-out page is left to the sign-in dialog. The page's decisions are
`VersionCheckState`'s (no DOM; `VersionCheckLoop` runs them with the RPC, the UI, and the flags passed in):
- The warning time starts only when the dialog is really shown: the page sends back the server's warning stamp only
  from the answer whose dialog it showed. The dialog is held back while another modal is open or the student is writing
  a reason (`writingReason`, `MainPanel.kt`), and a due reload, its countdown included, is put off while a reason is
  being written.
- Before reloading itself, the page records its bundle's `apiVersion` in `sessionStorage`
  (`history-walk.reloaded-for-api-version`). If the reload brings back the same bundle (Cloudflare still serving the old
  `main.bundle.js`), the page sends no stamp, so it shows the dialog once and never reloads itself again for that bundle
  in that tab; an up-to-date answer clears the record.

## Tests

JVM tests only (`src/jvmTest`), Kotest `StringSpec` + MockK + Ktor test host. Route tests call `appRoutes(...)` with
injected `findEmail`/`teacherEmails` lambdas instead of a database.
- The database specs (`UserRepositoryTest`, `DbmsTxsTest`, `ContentServiceTest`, `PagesTest`, `UserResetTest`,
  `SignOutTest`) each start their own `TestPostgres`, a Testcontainers `postgres:18` with the real Flyway migrations
  applied, and share the small deck and helpers in `TestWalk.kt`.
- `ContentServiceTest` mocks `ApplicationCall.userUuid` (a `mockkStatic` of `UserSessionKt`) to drive the RPC service
  as a signed-in user.
- `SignOutTest` signs a student out and replays the old cookie, which must no longer sign anyone in.
- `PagesTest` renders the teacher pages from Postgres, checking that a student's reason or name reaches the page as
  text, never markup, and drives `/summary`, `/reasons`, `/slides`, and the `/slide/{id}/{version}` jump as a signed-in
  teacher.
- `AuthCallbackTest` runs a real `/auth/login` and then `/auth/callback`, with a Ktor `MockEngine` as Google's token
  and userinfo endpoints (the `HttpClient` passed to `configureGoogleAuth` and `signInRoutes`) and
  `User.upsertFromGoogle` mocked (`mockkObject`): a matching state cookie gets a session, a missing or another sign-in's
  cookie gets none and provisions no one, and the token request's PKCE verifier must hash to the challenge. It takes
  the state and the cookie from the login's response, except in the replays of a stolen callback URL (issue #7 of
  `docs/code-review-2026-10-31.md`), which must be refused before any token request reaches Google.
- `UserResetTest` checks that a POST to `/userreset` needs a session and deletes only the caller's rows, and that a
  GET or HEAD deletes nothing. `NoStoreCachingTest` (no database: it stubs the two class pages with
  `mockkObject(Pages)`) checks the `no-store` header on every per-user route.
- `SlideContentTest` walks both real decks from the root and fails on a path name that does not find its slide, a
  slide shared between two parents, a go-back past the root, a missing image, or a success slide whose title differs
  from the deck's (the summary page counts successes by that title).

**CI** (`.github/workflows`, all actions pinned to commit SHAs): `tests.yml` runs `jvmTest` with Kover on pushes and
pull requests to master; `docker.yml` runs `make docker-build`, `make procfile-check` (the Procfile's jar exists), and
`make docker-smoke`, which starts the image against a throwaway Postgres and checks `/ping`, `/version`, the page
shell's `Cache-Control`, and a 401 for an unsigned RPC call;
`zizmor.yml` audits `.github` changes; `lint.yml` runs `make lint`; `dependency-graph.yml` submits `jvmRuntimeClasspath`
so Dependabot sees the JVM libraries; `cleanup-runs.yml` deletes runs older than 30 days every Monday, keeping the
newest 10. `.gitignore` ignores dot-directories, so `.github/` is re-included explicitly. `make ci` runs the same gates
locally. The lint and format tasks skip `build/generated` by path (see `build.gradle.kts`); the slide decks' text
constants are SCREAMING_SNAKE_CASE, as ktlint's property-naming rule requires.

## Gotchas

- IntelliJ has twice auto-imported JDK-internal classes (`sun.jvmstat...`, `sun.jvm.hotspot.HelloWorld.e`) into Kotlin
  files, which breaks the build; delete such imports.
- Heroku: each app's env vars are listed in `heroku.md` and the README's "Google sign-in" section; the Procfile runs
  `java -jar build/libs/history-walk.jar -port=$PORT`, so the Docker image and Heroku both run the same fat jar.
- `./gradlew jar` (what Heroku's `GRADLE_TASK` and the Makefile run) is Gradle's prefix match for `jarWithJs`, the Kilua
  RPC plugin's fat jar: Kotlin Multiplatform has no `jar` task. `build.gradle.kts` names it `history-walk.jar` with no
  version, so a version bump does not leave the Procfile and the Dockerfile pointing at a file the build no longer
  writes; `make procfile-check` (in `make ci` and `docker.yml`) guards the Procfile. Keep the three names in step.
- `docs/superpowers/` holds the Google sign-in design spec and implementation plan. They predate folding their own
  `V002` into `V001`; today's `V002` is the session version, which came later.
