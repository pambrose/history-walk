# AGENTS.md

This file provides guidance to AI coding agents working with code in this repository.

## What this is

History Walk is a choose-your-own-path history scenario for Athenian students. It is a Kotlin Multiplatform app: a Ktor
server (`jvmMain`) and a KVision single-page client (`jsMain`) that talk over Kilua RPC, with Postgres behind Exposed and
Flyway. The same repo is deployed as two Railway services (Jermain and Moses) that differ only in env vars.

## Commands

The build needs JDK 25 (`gradle/gradle-daemon-jvm.properties` pins the daemon toolchain).
`make` with no target prints the help. The project `group` and `version` live in `gradle.properties`.

```bash
make db                 # Postgres 18 in Docker (container history-walk) with the jermain and moses databases
make reset-jermain      # drop and recreate one database (reset-moses for the other); Flyway rebuilds the schema on next start
make server             # Ktor on :8080, continuous rebuild (./gradlew jvmRun -t)
make client             # webpack dev server on :3000 with hot reload, proxying server routes to :8080
make tests              # JVM and client specs: ./gradlew jvmTest --rerun jsBrowserTest --rerun (Docker and Chrome)
make js-tests           # the client specs alone, Kotest in headless Chrome (./gradlew jsBrowserTest --rerun); no Docker
make coverage           # the tests plus Kover reports of the JVM code (build/reports/kover/html/index.html), then koverVerify
./gradlew jvmTest --tests 'com.pambrose.historywalk.auth.AuthRoutesTest'   # one JVM spec (a client run is always whole)
make lint               # kotlinter (./gradlew lintKotlin); make format fixes what it can
make zizmor             # audit .github (workflows, dependabot.yml) as the zizmor workflow does
make ci                 # every CI gate but the Trivy scan: lint, zizmor, JVM and client tests, docker-build, docker-smoke
make build              # clean + fat jar at build/libs/history-walk.jar (server + bundled JS client; no version)
make docker-build       # fat jar, then image history-walk:<version>; make docker-run runs it with .env against make db
make docker-push        # fat jar, then buildx pushes pambrose/history-walk:<version> and :latest (amd64, arm64) to Docker Hub
make docker-smoke       # start the image against a throwaway Postgres and check /ping, /version, /health, headers, the 401
make yarn-unlock        # rewrite the committed Kotlin/JS yarn lock after an npm dependency change (commit the result)
make railway-restart    # restart the Jermain and Moses services on Railway (railway CLI), then check /ping and /version
make railway-redeploy   # redeploy both from the newest Docker Hub image (after a release), then wait for its version
make railway-versions   # make served-version for each walk: the client bundle version it serves, beside VERSION
make log                # the last LOG_LINES (100) lines of each walk's Railway log; SERVICE=Moses for one walk
make log-jermain        # follow one walk's Railway log like tail -f (make log-moses for the other); Ctrl-C stops
make release-check     # list every reason this checkout can't be released as VERSION (publishes nothing)
make release           # release-check, clean + ci, ask, push the image, create the GitHub release (tag X, title vX)
make website-serve      # the Zensical docs site (website/history-walk) at localhost:8000, rebuilt on every save
make website-build      # build the docs site into website/history-walk/site, failing on any warning
make website-clean      # remove the built docs site and Zensical's cache
make upgrade-site       # upgrade Zensical and its dependencies in website/uv.lock, then install them
```

Local settings and secrets live in `.env` (gitignored, loaded by the IntelliJ run configs and `make docker-run`). The
server refuses to start without `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, and `OAUTH_CALLBACK_URL`. The DB defaults in
`Property.kt` point at a database named `historywalk`, so set `DBMS_URL` (e.g.
`jdbc:postgresql://localhost:5432/jermain`). The app and the tests use the standard PostgreSQL JDBC driver
(`org.postgresql`, pgjdbc); a `jdbc:pgsql:` URL, written for the pgjdbc-ng driver used through 1.1.3, is refused.

## Architecture

**RPC boundary.** `IContentService` (`commonMain/Services.kt`, `@RpcService`) is the whole client/server API. KSP
generates `ContentServiceManager`; `Main.kt` registers `ContentService(call)` per request, so the implementation reads
the signed-in user from the `ApplicationCall` (`call.userUuid`), never from a client-supplied id. DTOs are in
`commonMain/Dao.kt`. The client calls go through `jsMain/Rpc.kt`, which wraps each in `withAuth`.

**Moves are checked on the server.** `ContentService` takes the deck (`masterSlides`) and the user's stored slide as the
truth, not what the client sends:
- `makeChoice` and `provideReason` must start from the user's current slide and name one of its forward choices. The
  stored title, choice text, and `deadEnd` come from the deck (`Slide.leadsToDeadEnd`: a dead end, or a slide whose
  one forward choice, a "Continue", leads to one, so a decision like Jermain's "Leave in the autumn" is stored as the
  wrong turn it is), and `advance` is derived as the client does
  (`choices.size == 1`, go-backs counted).
- `goBackInTime` must name an ancestor, the current slide, or `/` (the client's "back to the start").
- A reason must pass `ReasonRule` (`commonMain`, at least 5 words, at most 2000 characters); the reason dialog uses the
  same rule.
- A repeat of a move just made (a double-click, a second dialog, a retried transaction) is accepted and changes nothing:
  `provideReason` inserts with `ON CONFLICT DO NOTHING`, so the first reason wins.
- Anything else is refused with a Kilua `ServiceException`, whose message reaches the browser.
- Every method runs inside `ContentService.answering`: a failure of the server's own (an `ExposedSQLException`, an
  `error("Missing uuid: ...")`) is logged there, with the user, and reaches the browser as an `IllegalStateException`
  saying only `SOMETHING_WENT_WRONG`, which the client treats as a failure, not a refusal. Kilua would otherwise send
  the exception's message and class name, which any student can read in the console.

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
- A reason is kept in the tab's `sessionStorage` (`ReasonDrafts`, `MainPanel.kt`) from the moment it is sent until
  the server has it, and the reason dialog for the same move starts from it. A 401 parks the call behind the sign-in
  dialog for good and signing in leaves the page, so without it a reason typed as the 12-hour session ran out was lost.

**Sign-in (Google OAuth, `jvmMain/auth/`).** Only `athenian.org` accounts (`SCHOOL_EMAIL_DOMAIN`) are admitted. The role
rule (`Roles.kt`): an address in `TEACHER_EMAILS` is a teacher, else a local part starting with two digits is a student,
else refused. The role is recomputed from the live `TEACHER_EMAILS` on every request, not taken from the stored row.
The progress views filter on the stored role, so `Main.kt` brings it into line at every startup (`User.syncStoredRoles`,
which leaves alone an address the rule no longer admits, so a teacher taken off the list stays out of the class pages).
The sign-in dialog names the domain the server serves at `/auth/domain` (`SCHOOL_EMAIL_DOMAIN`), in its instruction and
in a refusal (`SignInRefusal`, kept rather than its message so the dialog can word it once the domain arrives).
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
  `/admin` and its POSTs `/admin/view` and `/admin/clear`, `/slides`, `/threaddump`) inside `teachersOnly` (→ 403 for
  students), `/ping`, `/health`, `/version`, and `/static/*` open to all.
- Every response carries `X-Content-Type-Options: nosniff`, `Content-Security-Policy: frame-ancestors 'none'`, and
  `Referrer-Policy: same-origin`, and in production `Strict-Transport-Security` (`Installs.installSecurityHeaders`), a
  second line behind the `SameSite=Strict` session cookie.
- `/userreset` deletes the caller's own choices and is a POST, as `/logout` is (the client posts with
  `ClientUtils.postTo`); a GET or HEAD of either only redirects to `/`, since any link, image, or prefetch can send one.
- Sign-in, sign-out, `/userreset`, and the teacher pages sit inside `noStore` (`RouteGuards.kt`), which sends
  `Cache-Control: no-store, private` on every response there, the Google redirect and the guards' 403 included, so after
  a sign-out on a shared computer Back cannot show class data from a cache, and no shared cache stores any of it.
- On the client, Kilua turns only a 401 into `SecurityException`; KVision's `SecurityMgr.withAuth` then calls
  `Security.login()`, which shows `SignInDialog` once and parks forever (see the comment on `LoginPrompt` before changing it).
- `App.start()` reloads a page the browser restores from its back/forward cache (`pageshow` with `persisted`), so Back
  after a sign-out shows the sign-in dialog rather than the previous user's walk.

An explicit anti-goal: a student must never see another student's data or progress. Teacher pages read the
`history.user_*` views, which count students' moves and leave teachers' test walks out, except the moves a teacher made
in the student view (below).

**Teachers see the student view.** The admin page (`/admin`, `Pages.displayAdmin`) has one app-wide switch, the single
row of `history.settings` (`AdminTxs.teachersSeeStudentView`), so teachers can walk as their class will before the app
is given to it. It starts at the teacher view.
- The button names what pressing it does ("Teachers see student view" while teachers see the teacher view), so the page
  also says which view is on. It posts the view it names (`view=student` or `view=teacher`), not a toggle, so a second
  press of a page left open sets the same view again.
- In the student view `ContentService.getUserInfo` reports a teacher as a student, so the page draws a student's header:
  no teacher links, no "not counted" note, no reset. Nothing on the client knows about the switch, and the RPC
  signatures did not change. The teacher pages still answer a teacher by address, so the admin page says to come back
  to `/admin`.
- `provideReason` marks the move `counted_as_student` when the user is a teacher (by the live `TEACHER_EMAILS`) and the
  switch is on, read at each call. The progress views count those moves as a student's, and a teacher with one appears
  in the summary, counting only those moves. A student's move never sets the mark; the views count it by the role.
  A move the teacher already made in the teacher view is not counted when made again: `makeChoice` returns its stored
  reason without asking, and `provideReason` keeps the first row. So the page says to clear before a trial run.
- Counted moves stay counted when the switch goes back. The admin page's clear (`AdminTxs.clearTeacherWalks`) deletes
  every move of each address in `TEACHER_EMAILS`, in either view, sends each of them back to the first slide, and
  deletes any counted move left by someone taken off the list since. The form asks the browser to confirm first.

**Slides.** Decks are Kotlin DSL values in `jvmMain/content/` (`jermainLonguenSlides`, `mosesSlides`) built with
`slides/SlideDeck.slideDeck { slide(...) { choice(...) } }`; content is HTML/markdown in raw strings, rendered through
`MarkdownParser`. A slide keeps its choices by their text, so `choice` refuses a second one with the same text, which
would silently replace the first (Moses's 14th decision lost a choice that way). `Main.kt` picks the deck with `slideDeckFor` (`content/SlideDecks.kt`): `SLIDES_VARIABLE_NAME` is
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
`V005` is such a migration: text replacements of whole titles or chains of titles over `users.last_path_name` and the
four path and title columns of `user_choices`, worked out and checked against the before and after snapshots. Its
check stays: `DbmsMigrateTest` stores a student at every path either deck had before it
(`slide-paths/before-v005/`), and one who made every move between them, runs every migration, and requires each place
to find a slide the current deck reaches and each move to be moved whole or left as it was. A later rename that strands
a place fails it too, so it needs a migration of its own or, if the students are to restart, a deliberate fixture edit.

**Database.** `Dbms.migrate` runs Flyway (`src/jvmMain/resources/db/migration`) before `Database.connect` on every
startup, so each deploy migrates. Never edit an applied migration (Flyway checksums them); add the next `V00N__...sql`
(`V002` added `users.session_version`, `V003` the `history.settings` row and `user_choices.counted_as_student`, and
rebuilt the progress views; `V004` is data only, marking as dead ends the stored decisions `Slide.leadsToDeadEnd` now
catches, by path name; `V006` adds `choice_text` to the `user_reasons` view, at its end, for the reasons page; `V005` is data only too,
moving the stored places and moves of renamed and rerouted slides, as
described under Slides). Tables are Exposed objects in `dbms/DbmsTables.kt`; queries are in `dbms/DbmsTxs.kt`,
`dbms/AdminTxs.kt`, and `User.kt`.

The connection (`Dbms.hikari`) comes from a platform's `DATABASE_URL` when it is set (Railway's
`${{Postgres.DATABASE_URL}}`), else from `DBMS_URL`, `DBMS_USERNAME`, and `DBMS_PASSWORD`. `dbms/ContainerDatabaseUrl.kt`
parses the URL: it decodes the user name and password, refuses a bad URL without echoing it, takes `DBMS_NAME` (when
not blank) as the database in place of the URL's, and `DBMS_QUERY_PATH` (when not blank, a leading `?` dropped) as the
query string in place of the URL's. The query string goes on the end of the JDBC URL still percent-encoded, since
pgjdbc decodes it. Its parameters are pgjdbc's (libpq's names for the common ones, such as `sslmode`); the driver
ignores a name it doesn't know. The log gets only its `description`, `host:port/db as user`, which leaves out the query
string (pgjdbc would take a `password` there). `ContainerDatabaseUrlTest` covers it.

The driver is pgjdbc (`org.postgresql`), the one the tests use. Through 1.1.3 it was pgjdbc-ng (`pgjdbc-ng-all`), whose
shaded Netty 4.1.63 failed the Trivy scan. `Dbms.requirePgjdbcUrl` trims `DBMS_URL` and refuses it at startup unless
it starts with `jdbc:postgresql:`, the only scheme pgjdbc takes, with a hint of its own for one still written for
pgjdbc-ng (`jdbc:pgsql:`); either message names the fix and no part of the URL. pgjdbc tries SSL first (`prefer`) when a URL sets no `sslmode`;
`Dbms.unencryptedUnlessAsked` sets `sslmode=disable` as a driver property, which a URL's own `sslmode` overrides, so the
connection is unencrypted unless asked (`sslmode=require`), as it was under pgjdbc-ng. That suits a platform's private
network: a deliberate choice, so don't add SSL by default without asking. `DbmsConnectionTest` checks both through
pgjdbc's own URL parsing.

`V001` begins with `drop schema if exists history cascade`, and Flyway records what it has applied in
`public.flyway_schema_history` (the connection's default schema, outside `history`):
- Flyway runs `V001` whenever that table is missing or empty: its own refusal to migrate a non-empty schema without
  one looks only at `public`. So a database whose `history` schema survived without it (a restore or copy that left it
  out, the table dropped or emptied) would lose every user and choice at startup.
- `Dbms.migrate` refuses instead, before Flyway writes anything: when `V001` is pending and the `history` schema holds
  tables, startup fails with an error naming the table and the fixes. Restore `public.flyway_schema_history` from the
  same backup, or, if `history` is known to be at the latest migration, run Flyway's `baseline` at that version.
  A fresh database (no `history` schema) and a migrated one (`V001` recorded) pass, and the check only reads.
- So a backup or restore must take `public.flyway_schema_history` along with the `history` schema.
  A host's whole-database backup or copy is fine; a `pg_dump -n history` is not.

**Dev proxy.** In `make client`, the browser talks to :3000; `webpack.config.d/proxy.js` forwards `/auth`, `/rpc*`,
`/static`, and the teacher/reset pages to :8080. A new server route that the browser loads directly must be added there.
The files in `webpack.config.d/` are concatenated into the generated webpack config; the app does not use jQuery (an old
`ProvidePlugin` for it broke Bootstrap under jQuery 4 and was removed).

**Page caching.** `main.bundle.js` has the same name in every release, so a release reaches a browser at its next page
load only because of these; do not drop any of them:
- `Routes.staticContent` serves the page shell (`/`, the jar's `assets/index.html`), the bundle, and the `/static`
  images with `Cache-Control: no-cache, private` and a SHA-256 ETag (`revalidatedEachTime`). Kilua's own static
  handling is off (`initRpc(initStaticResources = false)`): it sent no Cache-Control, so a cache could keep the bundle
  for hours, and it answered every unknown path with the page shell.
- `ConditionalHeaders` (`Installs.installConditionalHeaders`) turns a matching `If-None-Match` into a 304 with no body.
- `AutoHeadResponse` (`Installs.installAutoHeadResponse`) answers HEAD, which `curl -sI` sends, with a GET's headers;
  without it every HEAD is a 404.

`private` keeps any shared cache (a proxy or CDN) from storing these responses; one put in front of a deployment must
honor the origin's headers, or it would serve stale bundles after a release. `StaticContentCachingTest` builds its own
routes from `staticContent()`, so it would not notice `appRoutes` dropping that call; `NoStoreCachingTest` checks the
shell, the bundle, and an image through `appRoutes`, and that the per-user routes (sign-in and sign-out, the reset, the
teacher pages) go out `no-store, private` instead (see `noStore` under Sign-in).

**Failures.** `Installs.installStatusPages` answers a request that throws: a `BadRequestException` is a 400 and a
`NotFoundException` a 404. Anything else is a 503 when a `SQLException` is among its causes (`isDatabaseFailure`), else
a 500, each logged at ERROR with the method and path (never the query string: a callback's carries Google's code and the
state). It used to answer every failure with a 404 logged at INFO. `SessionValidation` reads the user on every request
that carries a session, the page shell's included, so with the database down a signed-in browser gets a 503 for
everything while a signed-out one still gets the page. The RPC calls never reach it: Kilua answers their failures
itself. `/ping` touches neither the database nor a session and is the image's health check, so a database outage does
not have the platform restart the server; `/health` runs `select 1` (`Dbms.answers`) and is a 503 when it fails or takes
over `Dbms.HEALTH_TIMEOUT` (3 seconds), for an uptime monitor. It is open to anyone, so `dbms/HealthCheck` runs one
probe at a time (a call that finds one running waits on it) and reuses a finished one's answer for
`Dbms.HEALTH_MAX_AGE` (5 seconds): no number of calls can pile up threads or take the walk's connections. The probe runs
on the IO dispatcher apart from the calls, since JDBC cannot be interrupted and Hikari waits up to 30 seconds for a
connection.

**Settings.** `verifySettings` (`EnvVar.kt`, run from `Main.kt` beside `verifyAuthConfig`) stops startup, naming the
setting, when a boolean setting is not `true` or `false` (any case; unset or blank is the default), or a version-check
interval is not a whole number in its range (`ServerVersionCheck.VERSION_CHECK_INTERVAL_SECS`, 10–3600, and
`RELOAD_WARNING_INTERVAL_SECS`, 0–3600). `toBoolean()` used to read `1` as false, and the intervals were read at a
page's first check, where a typo failed every check and a 0 had every open page poll in a tight loop. The page has a
floor of its own (`VersionCheckState.MIN_CHECK_INTERVAL`, 5 seconds), whatever the server answers.

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
  (`history-walk.reloaded-for-api-version`). If the reload brings back the same bundle (a cache still serving the old
  `main.bundle.js`), the page sends no stamp, so it shows the dialog once and never reloads itself again for that bundle
  in that tab; an up-to-date answer clears the record.

## Tests

Kotest `StringSpec` on both targets. A spec in `src/commonTest` (`ReasonRuleTest`) runs on both.

**JVM** (`src/jvmTest`, run by `jvmTest`): Kotest on JUnit Platform + MockK + Ktor test host. Route tests call
`appRoutes(...)` with injected `findEmail`/`teacherEmails` lambdas instead of a database.
- The database specs (`UserRepositoryTest`, `DbmsTxsTest`, `ContentServiceTest`, `PagesTest`, `AdminTest`,
  `UserResetTest`, `SignOutTest`) each start their own `TestPostgres`, a Testcontainers `postgres:18` with the real Flyway migrations
  applied, and share the small deck and helpers in `TestWalk.kt`.
- `DbmsMigrateTest` runs `Dbms.migrate` on databases of its own in that container (`TestPostgres.emptyDatabase`): a
  fresh one migrates, a migrated one runs nothing and loses nothing, one whose Flyway history table was dropped or
  emptied is refused with its data untouched, and restoring the table or a deliberate baseline lets it start again.
- `ContentServiceTest` mocks `ApplicationCall.userUuid` (a `mockkStatic` of `UserSessionKt`) to drive the RPC service
  as a signed-in user, with the teacher addresses passed to `ContentService`. In the student view a teacher is reported
  as a student and their moves are counted; a student's never are.
- `AdminTest` drives the switch and the clear through `AdminTxs` and the `/admin` routes: a teacher's counted moves in
  the class pages and their other moves out of them, the button setting the view it names, an unknown view refused, the
  clear leaving students' walks alone, and a student or a signed-out visitor refused the page and both buttons.
- `SignOutTest` signs a student out and replays the old cookie, which must no longer sign anyone in.
- `PagesTest` renders the teacher pages from Postgres, checking that a student's reason or name reaches the page as
  text, never markup, and that the summary and reasons pages carry their titles (`SUMMARY_TITLE`, `REASONS_TITLE`, in
  the heading and the tab), and drives `/summary`, `/reasons`, `/slides`, and the `/slide/{id}/{version}` jump as a
  signed-in teacher.
- `AuthCallbackTest` runs a real `/auth/login` and then `/auth/callback`, with a Ktor `MockEngine` as Google's token
  and userinfo endpoints (the `HttpClient` passed to `configureGoogleAuth` and `signInRoutes`) and
  `User.upsertFromGoogle` mocked (`mockkObject`): a matching state cookie gets a session, a missing or another sign-in's
  cookie gets none and provisions no one, and the token request's PKCE verifier must hash to the challenge. It takes
  the state and the cookie from the login's response, except in the replays of a stolen callback URL (issue #7 of
  `docs/code-review-2026-10-31.md`), which must be refused before any token request reaches Google.
- `UserResetTest` checks that a POST to `/userreset` needs a session and deletes only the caller's rows, and that a
  GET or HEAD deletes nothing. `NoStoreCachingTest` (no database: it stubs the class pages and the admin page with
  `mockkObject(Pages)`) checks the `no-store` header on every per-user route.
- `StatusPagesTest` (no database: its `findUser` and `/health` probe follow a switch) checks that with the database down
  every request of a signed-in browser is a 503 while a signed-out one still gets `/ping`, that a bug is a 500, a bad
  request a 400, and a missing path still a 404, and that `/health` reports a database that fails. `HealthCheckTest`
  checks the probe behind it: its timeout, one probe at a time however many calls wait, and an answer reused for its
  maximum age (a `TestTimeSource`). `EnvVarTest` checks the strict reading of the boolean and number settings. `SecurityHeadersTest` checks the
  security headers on a page, a 404, and a 500, and HSTS in production only.
- `SlideContentTest` walks both real decks from the root and fails on a path name that does not find its slide, a
  slide shared between two parents, a go-back past the root, a missing image, an image whose `alt` is empty or `Pic`,
  or a success slide whose title differs from the deck's (the summary page counts successes by that title).

**Client** (`src/jsTest`, run by `jsBrowserTest`, which `jsTest` runs): Kotest under Karma in headless Chrome. The
Kotest Gradle plugin writes the JS entry point with KSP (`kspTestKotlinJs`), beside Kilua RPC's processor. MockK does
not run on Kotlin/JS, so the specs use fakes and the units' injected functions (`LoginPrompt`'s `show`, `SignInDialog`'s
`navigate`); the `MainPanel.kt` units they drive (`busy`, `onClickWhenIdle`, `reasonDialog`) are `internal`, not
private.
- `TestPage.kt`: `TestPage.root`, the one KVision `Root` for the run (KVision draws every modal and toast through the
  page's first Root and forgets them when it is disposed), rendering synchronously; and the DOM helpers (`awaitUntil`,
  `click`, `enter` for text that arrives by input event alone, `toastsShownBy`, `showModal`, `closeModals`, `texts`).
- `FakeServer.kt` replaces the page's `fetch`, so a call takes the real path (`Rpc`, `withAuth`, Kilua's client):
  `FakeServer.on(IContentService::makeChoice) { call -> returns(...) }` answers a route with a result,
  `Reply.Refused` (a `ServiceException`), or `Reply.HttpError` (a 401 is a `SecurityException`), and `callsTo` lists
  what the page sent, decoded with `call.param<T>(i)`. By default the user is `testStudent` on `testSlide()`. A 401
  parks the call behind the sign-in dialog for good and leaves the singleton `Security` signed out for the rest of the
  run, so only `WithAuthTest` sends one, and it runs last: Kotest runs the specs in name order.
- `LoginPromptTest`, `SignInDialogTest`, `ErrorToastTest` (`orErrorToast`, `launchOrErrorToast`), `OnClickWhenIdleTest`
  (the busy gate), `ReasonDialogTest` (the five-word gate), `ChoiceFlowTest` (a click on a choice through
  `MainPanel`: makeChoice, the reason dialog, provideReason, the next slide), and `HeaderTest` (a teacher's header
  links to the class pages and help, each opening in a new tab (`window.open`, `noopener`; help is the teacher guide,
  `Docs.TEACHER_GUIDE`), and the admin page, then the reset when it is on, whose tooltip (`RESET_TOOLTIP`) says what it
  deletes and which posts only once `window.confirm(RESET_CONFIRMATION)` is answered OK, and logout last; a student's to
  none of them).
- The version check: `VersionCheckStateTest` (the pure decisions: when the dialog is held back, when the server's
  warning stamp is kept, the reload put off while a reason is written, the reload remembered in `sessionStorage`),
  `VersionCheckLoopTest` (the loop with a fake server and UI, on Kotest's virtual clock, `coroutineTestScope`), and
  `KVisionVersionCheckUiTest` (the real dialog, gone from the DOM and KVision's modal list once closed).
- `WithAuthTest` (only a 401 reaches the sign-in dialog; a 503 or a refusal reaches the caller as itself, even while
  the page counts as signed out) and `PageShowReloadTest` (a page restored from the back/forward cache reloads).
- Kotest reports to Karma only once every spec has run, so each JS spec sets `timeout = CLIENT_TEST_TIMEOUT_MS`, and a
  test that hangs fails by name; `karma.config.d/kotest.js` lets the whole run go two minutes without a word before
  Karma gives up. The timeout is not in a Kotest `ProjectConfig`, which the KSP processor drops from the JS entry point
  after an incremental run. `--tests` does not reach the browser, so a client run is always whole.
- If the entry point is not generated, Kotlin skips `jsBrowserTest` as having no tests, which would pass with nothing
  run; `build.gradle.kts` replaces that condition so the run fails instead.

**CI** (`.github/workflows`, all actions pinned to commit SHAs): `tests.yml`'s `test` job runs `jvmTest` with Kover and
`jsTest` (the client specs, in the runner's Chrome) on pushes and pull requests to master; `docker.yml` runs `make
docker-build` and `make docker-smoke`, which starts the image against a throwaway Postgres and checks `/ping`,
`/version`, `/health`, the page shell's `Cache-Control`, and a 401 for an unsigned RPC call; `security.yml` builds the image and
has Trivy scan it (the Alpine and JRE layers and the fat jar's libraries) on pushes and pull requests to master and every
Monday, since the digest-pinned base means a new CVE changes nothing in the repository: a HIGH or CRITICAL vulnerability
with a fix fails the job, master's and the weekly runs upload their findings to code scanning, and `make
release-check` requires its `image-scan` to have passed for the release commit (`RELEASE_CHECKS`). Its `lock-scan` job
scans the client's npm lock, which the image scan cannot see inside the webpacked bundle, and only reports (a table, and
SARIF to code scanning off pull requests): the lock's findings, all in build and test tools, are issue 28 of
`docs/code-review-2026-11-11.md`, and once they are fixed its `exit-code` becomes `'1'`; `zizmor.yml` audits
`.github` changes; `lint.yml` runs `make lint`; `dependency-graph.yml` submits `jvmRuntimeClasspath` so Dependabot sees
the JVM libraries; `cleanup-runs.yml` deletes runs older than 30 days every Monday, keeping each workflow's most recent
run (uploaded reports expire after 30 days, `retention-days`), and every Trivy database in the Actions cache but
master's newest (about 1 GB each, one per day scanned); `website.yml` builds the docs site with `make website-build` when
`website/` changes, as a check on pull requests, and on master deploys it to GitHub Pages
(`https://pambrose.github.io/history-walk/`, which needs Settings → Pages → Source set to GitHub Actions).
`dependabot.yml` proposes action updates and new digests for the Dockerfile's base image (pinned by digest, tag kept)
weekly, 7 days after each release, but for the JVM libraries only security updates (`open-pull-requests-limit: 0`; `make
versions` lists the rest). `.gitignore` ignores dot-directories, so `.github/` is re-included explicitly, and so is
`.kotlin-js-store/yarn.lock`, the client's npm lock: `build.gradle.kts` sets the Yarn plugin to fail a build whose
resolved packages differ from it, or that finds none, so CI builds the bundle a release ships. After changing a Kotlin/JS
or npm dependency, run `make yarn-unlock` and commit the rewritten lock with the change. `make ci` runs
the same gates locally, except the Trivy scan, which runs only in CI. `koverVerify` (in `tests.yml`, `make ci`, and `make coverage`) fails when the JVM line coverage
that `koverLog` prints falls under the floor in `build.gradle.kts`'s `kover` block (90%, about 91.8% today): a ratchet,
so raise it as coverage grows and never lower it to let a change through. The lint and format tasks skip
`build/generated` by path (see `build.gradle.kts`); the slide decks' text constants are SCREAMING_SNAKE_CASE, as
ktlint's property-naming rule requires.

## Gotchas

- IntelliJ has twice auto-imported JDK-internal classes (`sun.jvmstat...`, `sun.jvm.hotspot.HelloWorld.e`) into Kotlin
  files, which breaks the build; delete such imports.
- Adding a defaulted parameter to a function the JVM specs call (`Routes.appRoutes`, in the 2026-11-11 Wave 1) once
  left Kotlin's incremental compile with test classes calling the old `$default` method. Dozens of specs failed with
  `NoSuchMethodError`, and the build cache kept the stale output, so even `./gradlew clean` restored it and the
  classes stayed up to date under `--no-build-cache`. `./gradlew compileTestKotlinJvm --rerun` recompiles them and
  replaces the cached copy.
- There is no Procfile: a deployment starts the fat jar with its own start command (Railway's service settings),
  `java -jar build/libs/history-walk.jar -port=$PORT`, and the Docker image runs the same jar.
- `./gradlew jar` (what the Makefile runs) is Gradle's prefix match for `jarWithJs`, the Kilua RPC plugin's fat jar:
  Kotlin Multiplatform has no `jar` task. `build.gradle.kts` names it `history-walk.jar` with no version, so a version
  bump does not leave the Dockerfile or a deployment's start command pointing at a file the build no longer writes.
  Keep the names in step.
- `docs/superpowers/` holds the Google sign-in design spec and implementation plan. They predate folding their own
  `V002` into `V001`; today's `V002` is the session version, which came later.
- `website/history-walk/docs/` is the guide for teachers and administrators (a Zensical site; `make website-serve`).
  Its `llms.txt` and `release-notes.md` are symlinks to the top-level `llms.txt` and `RELEASE_NOTES.md` (the site
  serves the first at its root, and shows the second as its Release notes page); edit the top-level ones.
  A change a teacher or an administrator would notice (a page, a setting, an error message) needs its page updated in
  the same pull request.
