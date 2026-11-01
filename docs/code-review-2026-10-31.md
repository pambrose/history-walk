# History Walk code review — 2026-10-31

Reviewed at `master` commit `6ac1f73`, after PRs #4–#8. The review covered:

- the Ktor server: routes, the RPC service, the database layer, sign-in and sessions
- the KVision client
- the shared code
- the build, Docker image, CI, and Heroku deployment

Every finding below was traced through the code; the file references are to that commit. Issues fixed during the same
session are not listed: the go-back offsets, the quotes in slide text, and lint.

**Status key:** ⬜ open · ✅ done. To record progress, change an item's ⬜ to ✅ in the summary table and in its section.

**Severity:**

- **High** — users are affected now, or a secret is exposed.
- **Medium** — a real defect or risk that needs a specific trigger.
- **Low** — hardening, hygiene, or a missing test.

## Summary

| #             | Status | Severity | Area           | Issue                                                                                  |
|---------------|:------:|----------|----------------|----------------------------------------------------------------------------------------|
| [1](#issue-1)   | ✅ | High     | Client         | One failed RPC call freezes the whole page, the version check included                 |
| [2](#issue-2)   | ⬜ | High     | Server / ops   | The database password is written to the logs                                           |
| [3](#issue-3)   | ✅ | High     | Server         | `/contentreset` runs Kotlin fetched from GitHub, now always fails, and bloats the jar  |
| [4](#issue-4)   | ✅ | Medium   | Server         | `/threaddump` is open to anyone                                                        |
| [5](#issue-5)   | ✅ | Medium   | Server         | The RPC service trusts client-supplied choices, so a student can fake progress         |
| [6](#issue-6)   | ✅ | Medium   | Client, server | A double-click records a choice twice or fails on the unique constraint                |
| [7](#issue-7)   | ✅ | Medium   | Auth           | A stolen callback URL can be completed by anyone (PKCE is derived from the state)      |
| [8](#issue-8)   | ✅ | Medium   | Client (a11y)  | The reason dialog's OK stays disabled for dictated or pasted text                      |
| [9](#issue-9)   | ✅ | Medium   | Ops            | The Procfile pins `history-walk-1.0.0.jar`, so a version bump breaks Heroku            |
| [10](#issue-10) | ✅ | Medium   | Content        | Renaming or moving a slide silently sends students back to the start                   |
| [11](#issue-11) | ⬜ | Medium   | Privacy        | On a shared computer, class data and a student's page outlive sign-out                 |
| [12](#issue-12) | ⬜ | Medium   | Tests          | The client has no tests                                                                |
| [13](#issue-13) | ✅ | Low      | Server         | `/userreset` is a GET that deletes all of the caller's choices, and is untested        |
| [14](#issue-14) | ✅ | Low      | Auth / tests   | The callback's cookie check is untested, because the HTTP clients cannot be injected   |
| [15](#issue-15) | ✅ | Low      | Auth           | Sessions last 14 days and cannot be revoked on the server                              |
| [16](#issue-16) | ⬜ | Low      | Client         | A forced reload can come without the update dialog ever shown, losing a typed reason   |
| [17](#issue-17) | ⬜ | Low      | Client         | Nothing stops a reload loop while a stale bundle is still served                       |
| [18](#issue-18) | ⬜ | Low      | Client         | Before the first successful call, any error shows the sign-in dialog                   |
| [19](#issue-19) | ✅ | Low      | Ops            | An unknown `SLIDES_VARIABLE_NAME` silently serves the Moses walk                       |
| [20](#issue-20) | ⬜ | Low      | Database       | `V001` begins with `drop schema if exists history cascade`                             |
| [21](#issue-21) | ⬜ | Low      | Client         | Panels and dialogs are never disposed, so each slide leaks a little                    |
| [22](#issue-22) | ⬜ | Low      | Supply chain   | Dependabot covers only GitHub Actions, and the Docker base image is not pinned         |
| [23](#issue-23) | ⬜ | Low      | CI             | No coverage floor, so coverage can fall without a failing check                        |
| [24](#issue-24) | ⬜ | Low      | Build          | Gradle deprecations block Gradle 10, and patch updates are pending                     |

Things checked and found sound:

- **Isolation:** every RPC takes the user from the session, never from the client, so no route lets one student read
  another's data.
- **Escaping:** the teacher pages escape student input (`PagesTest` covers this).
- **Session cookie:** it is encrypted and signed, with a constant-time comparison.
- **Redirects:** no redirect target is taken from the request.
- **Slide HTML:** the client renders it unescaped, but it comes only from the compiled decks.
- **Sign-in errors:** they render as text.

## Issues

<a id="issue-1"></a>
### ✅ 1. One failed RPC call freezes the whole page, the version check included

- **Severity:** High
- **Where:**
  - `src/jsMain/kotlin/com/pambrose/historywalk/App.kt:17`, which defines the scope
  - `App.kt:38` and `:43`, which launch the start-up work and the version check
  - the click handlers in `MainPanel.kt:62`, `:92`, `:134`, `:161` and `:226`
  - `ClientUtils.kt:21-30`
- **Problem:**
  - `val AppScope = CoroutineScope(window.asCoroutineDispatcher())` gets a plain `Job`, not a `SupervisorJob`.
  - `ClientUtils.withAuth` logs a failed call and rethrows it, and no `launch` catches anything.
  - With a plain `Job`, a child that fails cancels the whole scope. Every later `AppScope.launch` starts already
    cancelled, so its body never runs.
- **Scenario:**
  - A student clicks a choice during a Wi-Fi drop, a dyno restart (503), or a server error.
  - From then on no button does anything, and the student sees no message.
  - The `ClientVersionCheck` loop runs in the same scope, so it stops too, and a stale page never gets its reload
    prompt.
- **Fix:**
  - Use `CoroutineScope(window.asCoroutineDispatcher() + SupervisorJob())`.
  - Catch errors in the click handlers and show a "something went wrong, try again" toast.
  - Give the version check a scope of its own.

<a id="issue-2"></a>
### ⬜ 2. The database password is written to the logs

- **Status:** the code no longer logs the password (Wave 1). The credentials still need rotating on both apps,
  since the old password is in the Papertrail history; check this item off after that.
- **Severity:** High
- **Where:** `src/jvmMain/kotlin/com/pambrose/historywalk/dbms/Dbms.kt:47-58`
- **Problem:**
  - `logger.info { "Heroku database url: $herokuDbmsUrl" }` logs Heroku's whole `DATABASE_URL`
    (`postgres://user:password@host:port/db`) at INFO.
  - Both apps send their logs to Papertrail (`heroku.md`), so the production database password is stored with a third
    party and visible to anyone with log access.
  - The code then takes the URL apart with `substringAfter`/`substringBefore`. That breaks on a password containing `@`
    or `:`, and on a URL with query parameters.
- **Fix:**
  - Delete the log line, or log only the host and database name.
  - Parse the URL with `java.net.URI`, splitting `userInfo` at its first `:`.
  - Rotate the credentials on both apps (`heroku pg:credentials:rotate`), since the old ones are in the log history.

<a id="issue-3"></a>
### ✅ 3. `/contentreset` runs Kotlin fetched from GitHub, now always fails, and bloats the jar

- **Severity:** High
- **Where:**
  - `Routes.kt:119-126`, the route
  - `Main.kt:45-90`, `HistoryWalkServer.loadSlides`
  - `MainPanel.kt:278-279`, the teacher's "reset" button
  - `build.gradle.kts:173-175`, the scripting dependencies
- **Problem:**
  - The teacher-only GET `/contentreset` fetches a Kotlin file from `pambrose/history-walk-content` on GitHub (or a local
    file) and evaluates it with the JSR-223 Kotlin script engine, inside `runBlocking` on a request thread.
  - It then replaces the deck every student is walking.
  - The decks now live in this repo, so the route has no purpose. Whoever can push to that other repo can run code on the
    server.
  - It also no longer works. The script ends with `SLIDES_VARIABLE_NAME`, which is now `jermain` or `moses`, but the old
    content file defines `jermainLonguenSlides`. The eval fails, and the route sends the teacher the full stack trace
    (`call.respondText(e.stackTraceToString())`).
  - The teacher's "reset" navLink points at this route, not at `/userreset`.
  - The script engine pulls in the Kotlin compiler: the fat jar is 96 MB, and about 11,000 of its classes are the
    compiler.
- **Fix:**
  - Delete the route, `loadSlides`, and the `kotlin-scripting-jsr223` and `script-utils-*` dependencies.
  - Remove the `SLIDES_REPO_*` and `SLIDES_LOCAL_FILENAME` variables from `EnvVar`, `heroku.md`, and the README.
  - Point the reset button at `/userreset` (see [13](#issue-13)) or remove it.
  - Then check the smaller jar and image with `make docker-smoke`.

<a id="issue-4"></a>
### ✅ 4. `/threaddump` is open to anyone

- **Severity:** Medium
- **Where:** `src/jvmMain/kotlin/com/pambrose/historywalk/Routes.kt:165-178`
- **Problem:**
  - `get("threaddump")` is registered outside `signedInApi` and `teachersOnly`. It returns the stack of every JVM thread:
    class and library names, the connection pool, and the request handlers in flight.
  - `dump(true, true)` collects locked monitors and synchronizers, which pauses the JVM, so repeated requests slow a small
    dyno for everyone.
  - AGENTS.md and the `appRoutes` KDoc list only `/ping`, `/version` and static content as public, and no test covers the
    route.
- **Fix:** Delete the route, or move it inside `teachersOnly`. Add a test that an anonymous request is refused.

<a id="issue-5"></a>
### ✅ 5. The RPC service trusts client-supplied choices, so a student can fake progress

- **Severity:** Medium (data integrity, not privacy)
- **Where:**
  - `src/jvmMain/kotlin/com/pambrose/historywalk/ServiceImpl.kt:33` (`makeChoice`), `:70` (`provideReason`) and `:98`
    (`goBackInTime`)
  - `Services.kt`, the interface
- **Problem:**
  - The server stores whatever the client sends as `toPathName`, `toTitle`, `deadEnd` and `choiceText`, and moves the
    user to whatever path `makeChoice(advance = true)` or `goBackInTime` names.
  - It never checks that the transition exists in the deck or that it starts from the user's current slide.
  - The rule that a reason needs five words is enforced only in the browser, and `reason` has no length limit.
- **Scenario:** A student replays a call from DevTools, sending `provideReason` the success slide's title with
  `deadEnd = false`. The teacher's summary then shows Success, and the correct-decision streak goes up. `goBackInTime`
  can jump straight to any slide.
- **Fix:**
  - Have the client send only the source path and the choice text.
  - Have the server look the transition up in `masterSlides`, starting from the user's stored `last_path_name`, and
    derive the target path, title, `deadEnd` and `advance` itself.
  - Allow `goBackInTime` only to an ancestor of the current slide.
  - Enforce the reason rule and a length cap on the server.
  - Add a test for each rejection.

<a id="issue-6"></a>
### ✅ 6. A double-click records a choice twice or fails on the unique constraint

- **Severity:** Medium
- **Where:**
  - `MainPanel.kt:158-175`, the choice buttons
  - `MainPanel.kt:181-233`, the reason dialog
  - `ServiceImpl.kt:79`, a plain `insertAndGetId`
  - the `user_choices_unique` constraint in `V001__create_schema.sql`
- **Problem:** Nothing stops a second click while a request is in flight. `makeChoice` returns an empty reason until
  `provideReason` inserts the row.
- **Scenarios:**
  - A double-click opens two "Reasoning" dialogs. Submitting the second inserts the same
    `(user, from_path_name, to_path_name)` again. The duplicate-key error, SQL included, goes to the browser, and
    [1](#issue-1) then freezes the page.
  - Clicking A and then B quickly records two decisions from one slide, which inflates the decision count.
- **Fix:**
  - Disable the choice and go-back buttons from the click until `refresh`.
  - Make `provideReason` idempotent by upserting on the unique key.
  - Add a server test that calls `provideReason` twice for the same transition.

<a id="issue-7"></a>
### ✅ 7. A stolen callback URL can be completed by anyone (PKCE is derived from the state)

- **Status:** done in Wave 3: the state cookie holds a random secret bound to the state by an HMAC, the verifier is
  derived from that secret, and the provider's `verifyState` hook refuses an unbound cookie before the code is
  redeemed. `AuthCallbackTest` replays a stolen callback URL with the bare state and with a made-up cookie, and checks
  that no token request reaches Google.
- **Severity:** Medium (unlikely, but a stolen teacher sign-in exposes every student)
- **Where:**
  - `auth/ConfigureAuth.kt:55-62`
  - `auth/OAuthState.kt:48-51`
  - `auth/AuthRoutes.kt:91-95`
- **Problem:**
  - The PKCE verifier is derived from the signed `state`, which travels in the URL.
  - The callback's cookie check only compares the `oauth_state` cookie with that same state, and a client controls its
    own cookies.
  - The KDoc says "a code taken from a callback is no good to anyone without the verifier". But this server works out
    the verifier for whoever presents the state.
- **Scenario:**
  - Someone obtains a victim's unused callback URL (`code` and `state`) within its 10-minute window, for example from a
    failed callback left in the history of a shared machine.
  - They replay it with `Cookie: __Host-oauth_state=<state>`.
  - The state verifies, the server supplies the verifier, Google accepts the code, and the attacker gets the victim's
    session.
  - Login CSRF is still blocked, because an attacker cannot plant a `__Host-` cookie in the victim's browser.
- **Fix:**
  - Put a random secret in the HttpOnly cookie, bind the signed state to it (for example with an HMAC of the secret), and
    derive the verifier from the secret.
  - Check the cookie in Ktor's `verifyState` hook, so the check runs before the code is redeemed.
  - Do [14](#issue-14) first, so the change has tests.

<a id="issue-8"></a>
### ✅ 8. The reason dialog's OK stays disabled for dictated or pasted text

- **Severity:** Medium (accessibility)
- **Where:** `MainPanel.kt:186-205`
- **Problem:**
  - OK starts disabled, and the word count that enables it is recomputed only on `keyup`.
  - Text that arrives without key events never updates it, and the student cannot move on. This covers voice dictation
    (ChromeOS, macOS, Windows), right-click → Paste, and drag-and-drop.
  - The five-word rule is never shown, so the student doesn't know why OK is disabled.
- **Fix:** Recompute on the `input` event, or bind to the TextArea's value, and show "at least 5 words" under the field.

<a id="issue-9"></a>
### ✅ 9. The Procfile pins `history-walk-1.0.0.jar`, so a version bump breaks Heroku

- **Severity:** Medium
- **Where:** `Procfile:1`, with `version` in `gradle.properties`
- **Problem:** `web: java -jar build/libs/history-walk-1.0.0.jar -port=$PORT`. The first release that changes `version`
  builds `history-walk-1.0.1.jar`, and the dyno fails to start. AGENTS.md mentions the pin, but nothing enforces it.
- **Fix:** Give the fat jar a version-free name (`archiveFileName` in `build.gradle.kts`) and use it in the Procfile and
  the Dockerfile, or use a shell glob in the Procfile. Have `make ci` run the Procfile's jar path.

<a id="issue-10"></a>
### ✅ 10. Renaming or moving a slide silently sends students back to the start

- **Status:** done in Wave 2: `SlidePathsTest` pins both decks' path names, so a rename fails CI and lists the
  paths that disappeared. No alias map was built; a rename that is needed still takes a migration of the stored paths.
- **Severity:** Medium
- **Where:**
  - `slides/SlideDeck.kt:68-77`, `findSlideByPathName`
  - progress is stored by path name in `users.last_path_name` and `user_choices`
- **Problem:**
  - A slide's identity is the chain of titles from the root.
  - Fixing a typo in a title, or moving a slide, changes the path names under it. Every student saved below that point
    then resumes at the root slide, with only an `error` log line.
  - Their old choices no longer match any slide, so "already visited" markers and the teacher's summary drift.
- **Fix:**
  - Add a snapshot test of each deck's path names, so a title edit fails CI and has to be made deliberately.
  - For edits that are needed, add a path-alias map (or a migration that rewrites the stored path names).

<a id="issue-11"></a>
### ⬜ 11. On a shared computer, class data and a student's page outlive sign-out

- **Status:** the server part is done in Wave 3: the teacher pages, `/userreset`, and the sign-in and sign-out routes
  send `Cache-Control: no-store, private` (`noStore` in `RouteGuards.kt`). The client's `pageshow` reload is still open
  (Step 11).
- **Severity:** Medium (the project's rule: no student sees another's data)
- **Where:**
  - `Routes.kt:111-117` and `:150-152`, the teacher pages, sent with no `Cache-Control`
  - `Routes.kt` `staticContent`, the page shell, sent with `no-cache` but not `no-store`
  - `App.kt`, which has no `pageshow` handler
- **Problem:**
  - Pages that are not `no-store` stay eligible for the back/forward cache and the HTTP cache.
  - After a teacher opens `/summary` or `/reasons` and signs out, Back can show the whole class's names, emails and
    reasons.
  - After a student signs out, Back can restore that student's page for the next person.
  - A Cloudflare "cache everything" rule would also cache the extensionless teacher pages at the edge.
- **Fix:**
  - Send `Cache-Control: no-store, private` on everything inside `teachersOnly`, on `/userreset`, and on the auth
    routes.
  - In the client, reload on `pageshow` when `event.persisted` is set.
  - Keep the bundle's ETag scheme as it is.

<a id="issue-12"></a>
### ⬜ 12. The client has no tests

- **Severity:** Medium
- **Where:** there is no `src/jsTest`, although `build.gradle.kts:197-199` declares `kvision-testutils` for it
- **Problem:**
  - None of this is tested:
    - `LoginPrompt`, which takes an injected `show` precisely so it can be tested
    - the `SignInDialog` navigate hook
    - the `ClientVersionCheck` state machine
    - the choice → reason → `provideReason` flow
    - the five-word gate
    - all error handling
  - Issues [1](#issue-1), [6](#issue-6), [8](#issue-8), [16](#issue-16), [17](#issue-17) and [18](#issue-18) are each
    bugs that a client test would have caught.
- **Fix:**
  - Add `jsTest` specs (Kotest on JS, or kotlin-test with KVision testutils) for the units above, starting with each fix
    in that list.
  - Run them in CI (`jsTest` in `tests.yml` and `make ci`).

<a id="issue-13"></a>
### ✅ 13. `/userreset` is a GET that deletes all of the caller's choices, and is untested

- **Severity:** Low
- **Where:** `Routes.kt:101-108`
- **Problem:**
  - A GET, or a HEAD through `AutoHeadResponse`, permanently deletes the signed-in user's choices. Any same-site link,
    prefetch, or `<img>` would trigger it.
  - The session cookie's `SameSite=Strict` blocks cross-site requests, so this is defence in depth.
  - The route is always registered, and no test checks that it needs a session or that it touches only the caller's
    rows.
- **Fix:** Make it a POST, as `/logout` is, and add route tests for both properties.

<a id="issue-14"></a>
### ✅ 14. The callback's cookie check is untested, because the HTTP clients cannot be injected

- **Severity:** Low
- **Where:**
  - `auth/ConfigureAuth.kt:77`, `client = HttpClient(CIO)`
  - `auth/AuthRoutes.kt:43`, the `fetchGoogleUserInfo` client
  - `AuthRoutesTest`
- **Problem:**
  - The callback's success path, and its refusal when the cookie is missing or wrong, never run in a test.
  - If the check's result were ignored (`call.consumeOAuthState()` without the `if`), every test would still pass, and
    login CSRF would be back.
- **Fix:**
  - Let callers pass an `HttpClient` (a Ktor `MockEngine` in tests) to `configureGoogleAuth` and
    `fetchGoogleUserInfo`.
  - Test that a matching cookie gives a session, that a missing or mismatched cookie gives none and never provisions a
    user, and the replay in [7](#issue-7).

<a id="issue-15"></a>
### ✅ 15. Sessions last 14 days and cannot be revoked on the server

- **Status:** done in Wave 3: a sign-in lasts 12 hours, and `POST /logout` increments `users.session_version` (`V002`),
  which ends the user's sessions in every browser. Someone who leaves a shared computer without signing out still
  leaves a session there, for up to 12 hours.
- **Severity:** Low (a design choice)
- **Where:** `auth/UserSession.kt:30` (`LIFETIME = 14.days`), `auth/SessionValidation.kt:82`
- **Problem:**
  - The session cookie persists for 14 days, and signing out only clears it in that one browser.
  - On a shared lab computer, the next person in the same browser profile is signed in as the previous student, and a
    copied cookie stays valid after sign-out.
- **Fix:**
  - Decide on the trade-off: a browser-session cookie (no `Max-Age`) or a short idle timeout.
  - Add a per-user session version that sign-out increments and `SessionValidation` checks.

<a id="issue-16"></a>
### ⬜ 16. A forced reload can come without the update dialog ever shown, losing a typed reason

- **Severity:** Low
- **Where:** `ClientVersionCheck.kt:45`, with `ServerVersionCheck.kt`
- **Problem:**
  - The client stores `response.reloadWarningInstant` even when it held the update dialog back because another modal,
    such as the reason dialog, was open. So the server's 60-second warning clock starts unseen.
  - When the clock runs out, `RELOAD` counts down and reloads the page, discarding the reason being typed.
- **Fix:** Store and echo the instant only once the dialog has actually been shown, or put off `RELOAD` while the reason
  dialog is open.

<a id="issue-17"></a>
### ⬜ 17. Nothing stops a reload loop while a stale bundle is still served

- **Severity:** Low
- **Where:** `ClientVersionCheck.kt:65-72`
- **Problem:**
  - If Cloudflare keeps serving the old `main.bundle.js` after a release, every load gets the update dialog and is forced
    to reload about every 75 seconds, until the cache is purged.
  - AGENTS.md lists a stale bundle as a known risk after a deploy.
- **Fix:** Remember in `sessionStorage` which server `apiVersion` the page already reloaded for. If the mismatch is still
  there after that reload, show the dialog without reloading again.

<a id="issue-18"></a>
### ⬜ 18. Before the first successful call, any error shows the sign-in dialog

- **Severity:** Low
- **Where:** `App.kt:38-41`, through KVision's `SecurityMgr.withAuth`
- **Problem:**
  - Until a first call succeeds, `isLoggedIn` is false, and KVision treats any exception as "sign in needed".
  - A student who loads the page during a dyno restart or a network blip is told to sign in again and left waiting, or
    gets a blank page if the other start-up call succeeded.
- **Fix:** In `ClientUtils.withAuth`, show a retry message for errors that are not a `SecurityException`, instead of
  passing them on to KVision's sign-in handling.

<a id="issue-19"></a>
### ✅ 19. An unknown `SLIDES_VARIABLE_NAME` silently serves the Moses walk

- **Severity:** Low
- **Where:** `Main.kt:113-120`
- **Problem:** `if (slideName == "jermain") jermainLonguenSlides else mosesSlides`, so a typo such as `Jermain` or
  `jermian`, or a stale value such as `jermainLonguenSlides`, serves the wrong walk with no error.
- **Fix:** Accept exactly `jermain` or `moses` and refuse to start otherwise, as the server already does when a Google
  setting is missing. Log the deck it chose.

<a id="issue-20"></a>
### ⬜ 20. `V001` begins with `drop schema if exists history cascade`

- **Severity:** Low
- **Where:** `src/jvmMain/resources/db/migration/V001__create_schema.sql:1`
- **Problem:**
  - Flyway records its history in `public.flyway_schema_history`. A database whose `history` schema survives without that
    table would re-run `V001` and drop every user and choice at startup.
  - That can happen with a restore that skipped `public`, a dropped history table, or a database copied by hand.
  - `V001` has been applied, so it cannot be edited.
- **Fix:**
  - Document that a backup and restore must include `public.flyway_schema_history`.
  - Optionally, have startup check before migrating whether `history.users` exists while Flyway's history is empty, and
    refuse to migrate if so.

<a id="issue-21"></a>
### ⬜ 21. Panels and dialogs are never disposed, so each slide leaks a little

- **Status:** partly done in Wave 2: `displaySlide` disposes its panels and each reason dialog is disposed. The
  `Alert.show` in `ClientVersionCheck.kt` is still never disposed; it belongs with Step 11.
- **Severity:** Low
- **Where:**
  - `MainPanel.kt:71`, `removeAll()` in `displaySlide`
  - `MainPanel.kt:191`, the reason `Dialog`
  - `ClientVersionCheck.kt:55`, `Alert.show`
- **Problem:**
  - `removeAll()` detaches children without disposing them, so the `bind(userInfo)` subscription keeps each old panel
    tree alive.
  - Each reason dialog and alert stays in KVision's static modal list.
  - Memory grows by one panel tree per slide; this is minor over one walk.
- **Fix:** Use `disposeAll()` in `displaySlide`, and dispose the reason dialog after `getResult()` returns.

<a id="issue-22"></a>
### ⬜ 22. Dependabot covers only GitHub Actions, and the Docker base image is not pinned

- **Severity:** Low
- **Where:** `.github/dependabot.yml:10`, `Dockerfile:2` (`FROM eclipse-temurin:25-jre-alpine`)
- **Problem:**
  - The dependency graph now reports the JVM libraries, but no Dependabot entry for Gradle exists, so their security
    alerts never become fix PRs.
  - The image's base is a moving tag, so the same commit can build different images.
- **Fix:** Add the `gradle` ecosystem with `open-pull-requests-limit: 0` (security updates only) and a `docker` ecosystem,
  as canvas-cache does, and pin the base image by digest.

<a id="issue-23"></a>
### ⬜ 23. No coverage floor, so coverage can fall without a failing check

- **Severity:** Low
- **Where:** `build.gradle.kts` (the `kover` block), `.github/workflows/tests.yml`
- **Problem:** Kover reports about 88% line coverage, but nothing verifies it, so a change can delete tests or add
  untested code with CI still green.
- **Fix:** Add a `kover { reports { verify { rule { minBound(85) } } } }` ratchet, run `koverVerify` in `tests.yml` and
  `make ci`, and raise the floor as coverage grows.

<a id="issue-24"></a>
### ⬜ 24. Gradle deprecations block Gradle 10, and patch updates are pending

- **Severity:** Low
- **Where:** the build (each run warns "Deprecated Gradle features were used in this build, making it incompatible with
  Gradle 10"), and `gradle/libs.versions.toml`
- **Problem:**
  - The deprecations are unidentified, and they will turn into errors on the next Gradle major release.
  - `make versions` lists Kotlin 2.4.20 → 2.4.21 and KSP 2.3.12 → 2.3.13.
- **Fix:** Run `./gradlew build --warning-mode all` and fix or report each deprecation, then take the two patch updates
  together (KSP follows Kotlin).

## Suggested order of work

The steps run roughly highest-value first. Steps in the same wave can be done in parallel, by different people or in
separate branches, because they touch different files. Where two steps share files, the order between them is noted.

### Wave 1: stop the exposure (start all three at once)

| Step | Status | Issues | Work | Files |
|------|:------:|--------|------|-------|
| 1  | ⬜ | [2](#issue-2) | Remove the `DATABASE_URL` log line, parse with `URI`, rotate both apps' database credentials | `Dbms.kt` |
| 2  | ✅ | [3](#issue-3), [4](#issue-4) | Delete `/contentreset`, `loadSlides`, the scripting dependencies, and the `SLIDES_REPO_*` variables; delete or guard `/threaddump`; fix the teacher's reset button | `Routes.kt`, `Main.kt`, `EnvVar.kt`, `build.gradle.kts`, `MainPanel.kt`, docs |
| 3  | ✅ | [9](#issue-9), [19](#issue-19) | Give the jar a version-free name; fail startup on an unknown `SLIDES_VARIABLE_NAME` | `build.gradle.kts`, `Procfile`, `Dockerfile`, `Main.kt` |

Steps 2 and 3 both edit `build.gradle.kts` and `Main.kt`, in different places. Run them in parallel and merge whichever
finishes second on top of the first, or have one person do both.

### Wave 2: make the walk reliable (start together once Wave 1 is merged)

| Step | Status | Issues | Work | Files |
|------|:------:|--------|------|-------|
| 4  | ✅ | [1](#issue-1), [6](#issue-6) (client), [8](#issue-8), [21](#issue-21) | `SupervisorJob` plus an error toast; a busy flag that disables the buttons; the `input`-event word count with a hint; `disposeAll()` | `App.kt`, `MainPanel.kt`, `ClientUtils.kt` |
| 5  | ✅ | [5](#issue-5), [6](#issue-6) (server) | Look transitions up in the deck on the server; idempotent `provideReason`; reason rules and a cap; tests for each | `ServiceImpl.kt`, `Services.kt`, `DbmsTxs.kt`, tests |
| 6  | ✅ | [10](#issue-10) | A path-name snapshot test for both decks | `SlideContentTest.kt` |

Steps 4 and 6 are independent of everything else in the wave.

Step 5 changes the `IContentService` signatures, which also changes the client's calls in `MainPanel.kt`. So either:

- finish Step 5's interface change first and rebase Step 4 onto it, or
- have one person own the edits to `MainPanel.kt`.

Changing the RPC interface also needs an `apiVersion` bump, so pages already open are asked to reload.

### Wave 3: harden sign-in and sessions (Steps 7, 8 and 10 can start now; Step 9 follows Step 2)

| Step | Status | Issues | Work | Files |
|------|:------:|--------|------|-------|
| 7  | ✅ | [14](#issue-14) | Injectable `HttpClient`s and callback tests with a `MockEngine` | `ConfigureAuth.kt`, `AuthRoutes.kt`, `AuthRoutesTest.kt` |
| 8  | ✅ | [7](#issue-7) | Bind the state to a cookie secret and derive the verifier from it; check the cookie in `verifyState` | `OAuthState.kt`, `Pkce.kt`, `ConfigureAuth.kt`, `AuthRoutes.kt` |
| 9  | ✅ | [11](#issue-11) (server), [13](#issue-13) | `no-store` on the teacher, reset and auth routes; `/userreset` as a POST, with tests | `Routes.kt`, `RouteGuards.kt`, `MainPanel.kt` (the reset link) |
| 10 | ✅ | [15](#issue-15) | Decide the session lifetime; add the session version column if revocation is wanted | `UserSession.kt`, `SessionValidation.kt`, `V002__...sql` |

Step 8 needs Step 7, so its tests exist first. Step 9 is independent and can run alongside 7 and 8. Step 10 needs a
decision from the teachers before any code changes; any schema change goes in a new `V002` migration.

### Wave 4: the client's remaining edges, with tests (after Step 4)

| Step | Status | Issues | Work | Files |
|------|:------:|--------|------|-------|
| 11 | ⬜ | [16](#issue-16), [17](#issue-17), [18](#issue-18), [11](#issue-11) (client) | Version-check fixes, the retry message, the `pageshow` reload | `ClientVersionCheck.kt`, `ClientUtils.kt`, `App.kt` |
| 12 | ⬜ | [12](#issue-12) | Set up `jsTest`, cover Steps 4 and 11 and `LoginPrompt`, and run it in CI and `make ci` | `src/jsTest/**`, `build.gradle.kts`, `tests.yml`, `Makefile` |

Steps 11 and 12 can run in parallel: Step 12 can start with `LoginPrompt` and `SignInDialog` while Step 11 is in
progress, then add the version-check tests once it lands.

### Wave 5: hygiene (any time, in parallel with everything else)

| Step | Status | Issues | Work | Files |
|------|:------:|--------|------|-------|
| 13 | ⬜ | [22](#issue-22) | Dependabot `gradle` (security only) and `docker`; pin the base image by digest | `.github/dependabot.yml`, `Dockerfile` |
| 14 | ⬜ | [23](#issue-23) | A Kover coverage floor, enforced in CI and `make ci` | `build.gradle.kts`, `tests.yml`, `Makefile` |
| 15 | ⬜ | [24](#issue-24) | Find and fix the Gradle deprecations; take Kotlin 2.4.21 and KSP 2.3.13 | `build.gradle.kts`, `libs.versions.toml` |
| 16 | ⬜ | [20](#issue-20) | Document the restore requirement; optionally add the startup guard | `AGENTS.md`, `Dbms.kt` |

Steps 13–16 touch separate files, except that 14 and 15 both edit `build.gradle.kts`. Land 14 after Wave 2, so the floor
is set at the coverage the new tests reach.

### Parallel tracks at a glance

| Track | Steps | Can start |
|-------|-------|-----------|
| Server security | 1 → 2 → 9 | now |
| Ops | 3 | now |
| Client | 4 → 11 → 12 | after Wave 1 (Step 2 touches `MainPanel.kt`) |
| Server data | 5 → 6 | after Wave 1 |
| Sign-in | 7 → 8, then 10 | now |
| Hygiene | 13, 14, 15, 16 | now (14 after Wave 2) |
