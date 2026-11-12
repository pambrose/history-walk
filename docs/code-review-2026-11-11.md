# History Walk code review — 2026-11-11

Reviewed at `master` commit `2f20c9e` (1.2.1, after PR #33). The review covered:

- the Ktor server: routes, the RPC service, the database layer, sign-in and sessions
- the KVision client
- the shared code
- the two slide decks, as content a student reads
- the build, Docker image, Makefile, CI workflows, and release scripts
- the documentation: `AGENTS.md`, `README.md`, `llms.txt`, and the docs site

Each finding was checked against the code at that commit, and the file references are to that commit. One finding
depends on outside behavior that could not be checked here; it is marked *unconfirmed*.

Every issue from the 2026-10-31 review is done except its issue 2 (rotating the leaked database password), which is
carried over here as issue 1.

**Status key:** ⬜ open · ✅ done. To record progress, change an item's ⬜ to ✅ in the summary table, in its section,
and in the step that fixes it under [Suggested order of work](#suggested-order-of-work).

**Severity:**

- **High** — users are affected now, or a secret is exposed.
- **Medium** — a real defect or risk that needs a specific trigger, or content that teaches students something wrong.
- **Low** — hardening, hygiene, a typo, or a missing test.

## Summary

| #               | Status | Severity | Area              | Issue                                                                                          |
|-----------------|:------:|----------|-------------------|------------------------------------------------------------------------------------------------|
| [1](#issue-1)   | ✅ | High     | Ops               | The database password leaked to Papertrail was never rotated (carried over)                     |
| [2](#issue-2)   | ✅ | Medium   | Server            | Every server error is answered "404 Not Found" and logged at INFO                               |
| [3](#issue-3)   | ✅ | Medium   | Server, client    | The version-check settings are not validated: a typo kills the check, a 0 floods the server     |
| [4](#issue-4)   | ✅ | Medium   | Supply chain      | The client's npm dependencies are not locked, so CI builds and scans a different bundle         |
| [5](#issue-5)   | ⬜ | Medium   | Content (Moses)   | Slide 14 silently loses one of its three choices                                                |
| [6](#issue-6)   | ⬜ | Medium   | Content (Jermain) | Refusing Ross's deal still puts the student on the Ross path, and the patrol slide contradicts it |
| [7](#issue-7)   | ⬜ | Medium   | Content, data     | Five fatal Jermain decisions are recorded as correct ones                                       |
| [8](#issue-8)   | ⬜ | Medium   | Content (Moses)   | Two correct choices say one thing and the next slide says the opposite                          |
| [9](#issue-9)   | ⬜ | Medium   | Content (Moses)   | The opening contradicts the later story of Moses's mother                                       |
| [10](#issue-10) | ⬜ | Medium   | Content (Jermain) | The title slide and the docs misspell Loguen as "Longuen"                                       |
| [11](#issue-11) | ⬜ | Low      | Content           | "Decison" in 26 Moses slide titles, and lowercase verbs in four Jermain titles                  |
| [12](#issue-12) | ⬜ | Low      | Content           | Typos and grammar errors in both decks' text                                                    |
| [13](#issue-13) | ⬜ | Low      | Content (Jermain) | Companions, horses, patrols, and seasons that do not match the student's path                   |
| [14](#issue-14) | ⬜ | Low      | Content (a11y)    | Every image's alt text is "Pic", and a few markup slips                                         |
| [15](#issue-15) | ⬜ | Low      | Teacher pages     | The reasons page cannot show which wrong answer a Moses student chose                           |
| [16](#issue-16) | ⬜ | Low      | Server            | A failed RPC call sends the exception's message and class name to the browser                   |
| [17](#issue-17) | ⬜ | Low      | Server, data      | The class pages go by the role stored at sign-in, not the role the app uses                     |
| [18](#issue-18) | ⬜ | Low      | Auth, client      | Sign-in messages name athenian.org whatever `SCHOOL_EMAIL_DOMAIN` says                          |
| [19](#issue-19) | ⬜ | Low      | Client            | A reason typed as the session runs out is lost at sign-in                                       |
| [20](#issue-20) | ⬜ | Low      | Server            | No security headers: `nosniff`, `frame-ancestors`, HSTS, `Referrer-Policy`                      |
| [21](#issue-21) | ⬜ | Low      | Release           | `make release` pushes a rebuilt image, and cannot resume after a failed GitHub release          |
| [22](#issue-22) | ⬜ | Low      | CI (docs site)    | The docs site ignores its lock file, and its workflow misses Makefile changes                   |
| [23](#issue-23) | ⬜ | Low      | Makefile          | `docker-smoke` can stop without cleanup or diagnostics, and `make db` can wait forever          |
| [24](#issue-24) | ⬜ | Low      | Supply chain      | The app's user owns the jar it runs, and the Gradle wrapper has no checksum                     |
| [25](#issue-25) | ⬜ | Low      | Docs (developer)  | `AGENTS.md`, comments, and the Makefile have drifted from the code                              |
| [26](#issue-26) | ⬜ | Low      | Docs (site)       | The admin pages and `llms.txt` have drifted from the code                                       |
| [27](#issue-27) | ⬜ | Low      | Code hygiene      | Dead code and stale comments                                                                    |
| [28](#issue-28) | ⬜ | Low      | Supply chain      | The npm lock has 48 fixable HIGH or CRITICAL findings, all in build and test tools (found in Step 4) |

Things checked and found sound:

- **Isolation:** every RPC takes the user from the session, never from the client. Moves are checked against the deck
  and the stored slide, and no route lets one student read or change another's data.
- **Concurrency:** a double-click, a second tab, or a retried transaction cannot record two choices from one slide.
  Hikari runs `REPEATABLE READ`, so the second `UPDATE users` fails to serialize. Exposed retries it, and the retry is
  refused, or kept as a repeat.
- **CSRF and framing:** the session cookie is `SameSite=Strict`. A cross-site form post to `/userreset`, `/logout`,
  or `/admin/*` therefore arrives signed out, and so does an `/admin` page framed by another site. The state cookie
  is `Lax`, as the Google redirect needs.
- **Sign-in:** the state is signed and stateless, PKCE is derived from a secret in a `__Host-` cookie, and `verifyState`
  refuses a callback before its code is redeemed. The hosted domain is checked on Google's answer, and session
  versions end every copy of a cookie.
- **Escaping:** the teacher pages and the sign-in errors render student and URL text as text.
- **Blocking calls:** the JDBC calls run on Ktor CIO's `Dispatchers.IO`-backed user dispatcher, so they do not stall
  the engine.
- **Workflows:** every action is pinned to a SHA, and every checkout sets `persist-credentials: false`. Permissions
  are per job and minimal. No `${{ }}` expression appears in a `run:`, and nothing uses `pull_request_target`.
- **Release gates:** `make release-check` checks the job ids, the tag, and the release SHA, and catches checks still in
  progress or missing.
- **Image:** the image runs as a non-root user on a base pinned by digest, with a health check and a container-sized
  heap. `.dockerignore` limits the build context to the jar.
- **Decks:**
  - Every image a slide names exists.
  - Every Jermain go-back lands on the student's town.
  - Every dead end has an ending and a way back.
  - Each deck has one success slide, reached by a recorded choice.
- **Docs site:** every nav entry and internal link resolves. The settings, routes, defaults, and quoted UI text match
  the code, except as listed under issues 25 and 26.

## Issues

<a id="issue-1"></a>
### ✅ 1. The database password leaked to Papertrail was never rotated (carried over)

- **Status:** done: confirmed closed by the owner on 2026-11-11, and issue 2 of the 2026-10-31 review checked off with it.
- **Severity:** High
- **Where:** issue 2 of `docs/code-review-2026-10-31.md`, the only item of that review still open
- **Problem:**
  - Before Wave 1 of the last review, `Dbms.kt` logged Heroku's whole `DATABASE_URL`, password included, at INFO.
    Both apps shipped their logs to Papertrail.
  - The code was fixed, but the credentials were never rotated.
  - Since then the walks have moved to Railway, and the Heroku files were removed (#29). Nothing in the repository
    shows whether the Heroku apps and their databases still exist.
- **Fix:**
  - If the Heroku apps and databases are deleted, the leaked password opens nothing. Check both off here and in the
    old review.
  - If either database still exists, rotate it (`heroku pg:credentials:rotate`) or delete it.
  - If the Railway databases were restored from a Heroku dump, confirm that the Railway credentials are new ones.

<a id="issue-2"></a>
### ✅ 2. Every server error is answered "404 Not Found" and logged at INFO

- **Status:** done in Wave 1.
  - `Installs.installStatusPages` answers a `BadRequestException` with a 400 and a `NotFoundException` with a 404.
    Anything else is a 503 when a `SQLException` is among its causes, else a 500, logged at ERROR with the method and
    path.
  - `/slide/{id}` answers a non-numeric id with a 400.
  - `/health` runs `select 1` within 3 seconds (`Dbms.answers`), and `docker-smoke` checks it; `/ping` is unchanged.
    `/health` is open to anyone, so `HealthCheck` runs one probe at a time and reuses its answer for 5 seconds (a
    finding of the automated security review of the first commit).
  - `StatusPagesTest` covers each case; `troubleshooting.md` and `deployment.md` describe them.
  - `make railway-restart` still waits on `/ping`; moving it to `/health` is left to Step 18.
- **Severity:** Medium
- **Where:**
  - `src/jvmMain/kotlin/com/pambrose/historywalk/Installs.kt:66-82`, the `StatusPages` catch-all
  - `auth/SessionValidation.kt:45-51`, which reads the user on every request that carries a session
  - `Routes.kt:206-208` (`/ping`) and `Dockerfile:29-31`, the health check
- **Problem:**
  - `exception<Throwable>` logs at INFO and answers `HttpStatusCode.NotFound`, so every failure outside the RPC
    routes becomes a 404 that no WARN or ERROR filter sees.
  - That covers a database error, a bug, and the `toInt()` of a non-numeric `/slide/{slideId}`.
  - `SessionValidation` runs before routing, for every request with a session cookie, the page shell's included. If
    the database is down, its lookup throws on all of them.
  - `/ping` never touches the database, so the health check stays green throughout.
- **Scenario:**
  - Railway restarts Postgres for maintenance during class.
  - Every signed-in student who reloads gets a bare "404 Not Found" in place of the page, while a signed-out browser
    still gets the app.
  - A teacher's `/summary` and a student's sign-in callback (the user upsert) are 404s too.
  - The log shows only `INFO Throwable caught: ...` lines, and the container reports healthy.
- **Fix:**
  - Answer an unexpected exception with a 500 (or a 503 for a database error) and a short "Something went wrong,
    please try again" page, and log it at ERROR.
  - Keep 404 for real misses and 400 for bad parameters, e.g. `toIntOrNull()` in the `/slide` route.
  - Keep `/ping` as the liveness check, so a database outage does not restart the app in a loop.
  - Add a `/health` that runs `select 1` with a short timeout and answers 503 when it fails, for an uptime monitor and
    for `make railway-restart` to wait on.
  - Test: a route test whose `findUser` throws gets a 5xx, not a 404.
  - Update `admins/troubleshooting.md` with the new status codes.

<a id="issue-3"></a>
### ✅ 3. The version-check settings are not validated: a typo kills the check, a 0 floods the server

- **Status:** done in Wave 1.
  - `verifySettings` (run from `Main.kt`) stops startup, naming the setting, when a boolean setting is not `true` or
    `false`, `CLIENT_VERSION_CHECK_INTERVAL_SECS` is not a whole number from 10 to 3600, or
    `CLIENT_RELOAD_WARNING_INTERVAL_SECS` one from 0 to 3600.
  - The page never waits less than 5 seconds between checks (`VersionCheckState.MIN_CHECK_INTERVAL`).
  - `EnvVarTest` and `VersionCheckStateTest` cover it; `configuration.md`, `troubleshooting.md`, and the README
    describe it.
  - Before deploying, check that neither walk's Railway settings hold a value the server now refuses, such as `1` for
    a boolean.
- **Severity:** Medium
- **Where:**
  - `EnvVar.kt:36` (`toBoolean()`) and `:38-39`: `toInt()`, under a `@Suppress("unused")` that is wrong
  - `ServerVersionCheck.kt:23` and `:26`, read lazily
  - `jsMain/.../VersionCheckState.kt:113` and `ClientVersionCheck.kt:83`, which use the server's interval as is
- **Problem:**
  - `CLIENT_VERSION_CHECK_INTERVAL_SECS` and `CLIENT_RELOAD_WARNING_INTERVAL_SECS` are parsed with `toInt()` at the
    first version check, not at startup.
  - A value such as `60s`, or `60` followed by a space, throws on every check. Kilua logs it, and no open page ever
    hears of a release.
  - A `0` or a negative passes. The client then runs `delay(0)` in a loop, so every open page calls the server as fast
    as it can.
  - The boolean settings (`SHOW_RESET_BUTTON`, `ALLOW_SLIDE_ACCESS`, `DISPLAY_CONSECUTIVE_CORRECT_DECISIONS`) use
    `toBoolean()`, so `1` or `yes` silently means false.
- **Scenario:** an administrator sets `CLIENT_VERSION_CHECK_INTERVAL_SECS=0` to "turn the check off". Thirty open pages
  then each send an RPC every few milliseconds, each with a session lookup and its own transaction, until the pages
  are closed.
- **Fix:**
  - Read and check these settings at startup, as `SLIDES_VARIABLE_NAME` is. Trim, parse, and require a range (e.g.
    10–3600 seconds), and stop the server with a message naming the setting.
  - Accept only `true` or `false` for the booleans, in any case.
  - On the client, floor the interval, e.g. `maxOf(it, 5.seconds)`, so no server answer can make a page spin.
  - Tests in `ServerVersionCheckTest`, `VersionCheckStateTest`, and a startup test for a bad value.
  - Document the ranges in `admins/configuration.md`.

<a id="issue-4"></a>
### ✅ 4. The client's npm dependencies are not locked, so CI builds and scans a different bundle

- **Status:** done in Wave 1.
  - `.kotlin-js-store/yarn.lock` is committed, and the stale `kotlin-js-store/` (a 2022 lock) is deleted.
  - `build.gradle.kts` fails a build whose resolved packages differ from the lock, or that finds none; a tampered and
    a missing lock were each checked to fail.
  - `make yarn-unlock` says to commit the rewritten lock.
  - `security.yml` has a `lock-scan` job, but it only reports: the lock has 48 fixable HIGH or CRITICAL findings
    today, so a gate would fail every run. They are issue [28](#issue-28), and the job becomes a gate once they are
    fixed.
- **Severity:** Medium
- **Where:** `.gitignore:1` (`.*/`) and `:11`; the untracked `.kotlin-js-store/yarn.lock`; `Makefile:362`
  (`yarn-unlock`); `.github/workflows/security.yml`
- **Problem:**
  - The Kotlin/JS build writes its lock to `.kotlin-js-store/yarn.lock`, about 500 packages, webpack among them.
    `.*/` ignores it, and it has never been committed.
  - So every CI run resolves the client's npm packages afresh within their semver ranges. The maintainer's laptop
    keeps its own older lock, and `yarn-unlock` refreshes a file no one else gets.
  - Trivy's jar scan reads Maven metadata, so the npm code webpacked into `assets/main.bundle.js` is invisible to the
    `image-scan` gate. Dependabot does not see it either.
- **Scenario:**
  - A broken or malicious patch release of a transitive webpack or loader dependency lands within range. CI runs it at
    build time.
  - Or `make release` builds from the laptop's lock, so the `test` and `image-scan` results that `release-check`
    requires were produced from a different client bundle than the one students get.
- **Fix:**
  - Re-include the directory (`!.kotlin-js-store/`), drop the stale `kotlin-js-store` line and directory, and commit
    `yarn.lock`.
  - Set `yarnLockMismatchReport = FAIL` and `reportNewYarnLock = true` on the Yarn root extension, so a missing or
    drifted lock fails the build.
  - Add a Trivy filesystem scan of `.kotlin-js-store/yarn.lock` to `security.yml`.

<a id="issue-5"></a>
### ⬜ 5. Moses slide 14 silently loses one of its three choices

- **Severity:** Medium
- **Where:** `content/Moses.kt:680-687`; `slides/Slide.kt:57-64` (`choice`) and `:69-75` (`validateSlide`);
  `src/jvmTest/resources/slide-paths/moses.txt:61`
- **Problem:**
  - Two choices on the 14th decision have the same text, "There is no way you can escape right now, you wait for a
    better opportunity".
  - `choice()` stores choices in a map keyed by their text, so the second replaces the first. The slide shows two
    choices, not three, and the snapshot has only `Incorrect2`.
  - `validateSlide` looks for duplicate texts among the map's keys, where none can ever be.
- **Fix:**
  - Have the content author write the intended third choice.
  - Make a duplicate fail where it happens: `require(text !in choices)` in `choice()`, with a test in
    `SlideDeckTest`.
  - The fix only adds a path (`Incorrect1`), so rewrite the snapshot; no student's progress is orphaned.

<a id="issue-6"></a>
### ⬜ 6. Refusing Ross's deal still puts the student on the Ross path, and the patrol slide contradicts it

- **Severity:** Medium
- **Where:** `content/JermainLonguen.kt:1382-1384`; the patrol slides 20, 23, and 25 (`:316-319`, `:338`); the town
  exits at `:774`, `:822`, `:846`, `:862`, `:891`, `:943`, `:1023`, `:1054`, `:1102`, `:1156`, `:1173`
- **Problem:**
  - Answering **No** to "Do you complete the deal?" leads to `slide5OldBenViaRoss`. The Ross slides that follow
    assume the deal was made, with lines such as "drawing your pistols" (`:325`), "the travel papers that Ross gave
    you" (`:346`), and "Ross' advice was useful" (`:360`).
  - The other way round, "Major Encounter: Patrol" (slide 20) is reached from every town building, all of them on the
    Ross path. Yet its bluff says "You have none" (`:338`), and its fight is bare-handed.
- **Scenario:** a student who refused Ross is told about the papers Ross gave them, and a student who took the papers
  is told they have none.
- **Fix:**
  - Send "No" to a non-Ross branch, e.g. `slide6OldBenWhenToGo.copyOf()`.
  - Give the town exits a Ross-aware patrol, or make slides 20, 23, and 25 neutral about papers and pistols.
  - Both change path names. Add a migration that moves the stored paths (or accept that students mid-walk on these
    branches restart), and rewrite `slide-paths/jermain.txt` in the same pull request.

<a id="issue-7"></a>
### ⬜ 7. Five fatal Jermain decisions are recorded as correct ones

- **Severity:** Medium
- **Where:**
  - `content/JermainLonguen.kt:652-674` (Autumn Departure, Spring Departure) and the three "Ramshackle Hut (cont.)"
    slides (`:798-808`, `:1006-1015`, `:1139-1148`)
  - `slides/Slide.kt:26` (`isDeadEnd`) and `ServiceImpl.kt:64`, `:109`
- **Problem:**
  - Each of these slides sits between a decision and the Paddy Rollers ending, with a single "Continue".
  - `isDeadEnd` is "no choices and not the success slide", so it is false for them. The decision that leads there,
    e.g. "Enter the ramshackle hut?" or "Leave in the autumn", is therefore stored with `dead_end = false`.
  - "Continue" is an advance, which is never recorded, so no row ever marks the wrong turn.
- **Scenario:**
  - A student enters the hut and is caught, yet *Consecutive Correct Decisions* keeps counting up, which contradicts
    `teachers/the-walk.md:21-23`.
  - The `dead_end` column under-reports these failures to anyone who queries it.
- **Fix:**
  - Add `Slide.leadsToDeadEnd`: a dead end, or a slide whose one forward choice leads to one. Use it for
    `row[deadEnd]` in `provideReason` and for `SlideChoice.deadEnd` in `slideData`. No path names change.
  - Optionally, a migration that sets `dead_end = true` on stored rows whose `to_path_name` is one of these five
    slides. It is harmless on the Moses database, where no row matches.
  - Test it in `ContentServiceTest` with a chain in the test deck.

<a id="issue-8"></a>
### ⬜ 8. Two correct Moses choices say one thing and the next slide says the opposite

- **Severity:** Medium
- **Where:** `content/Moses.kt:644` → `:185`, and `:890` → `:67`
- **Problem:**
  - The correct choice "Look for downstream for a bridge or watercraft" leads to "You move upstream looking for a safe
    crossing", although upstream was the wrong answer (`:640`).
  - The correct choice "Head up river looking for … a bridge or a watercraft" leads to "looking for a shallow or
    narrow place to cross", which is the wrong answer's idea (`:886`).
- **Scenario:** a student who reasoned carefully, and wrote that reason down, is told they did the thing they rejected.
- **Fix:** align the choice text and the slide text. No titles change.

<a id="issue-9"></a>
### ⬜ 9. The opening contradicts the later story of Moses's mother

- **Severity:** Medium
- **Where:** `content/Moses.kt:15-19`; `:101`, `:107`; `website/history-walk/docs/index.md:16`
- **Problem:**
  - The opening says "you and your mother were sold to … Mr. Gooch".
  - Later slides say "You haven't seen your mother since you were sold away" (`:101`), and that the plantation is
    where you "spent the first ten years" (`:107`).
  - The decision at slide 8 ("sneak away to see your mother") depends on the later version. The docs' home page
    repeats the first one.
- **Fix:**
  - Rewrite the opening to match the later slides. Historically, Moses Roper was sold away from his mother.
  - Fix `index.md:16` to match.

<a id="issue-10"></a>
### ⬜ 10. The title slide and the docs misspell Loguen as "Longuen"

- **Severity:** Medium
- **Where:** `content/JermainLonguen.kt:12`; `README.md:79`; `website/history-walk/docs/index.md:15`;
  `CHANGELOG.md:125`
- **Problem:** the subject of the walk is the Rev. Jermain Wesley **Loguen**, born Jarm Logue. The first text every
  Jermain student reads gets his name wrong, and so do the docs.
- **Fix:**
  - Correct the displayed text. The root slide's title ("UGRR Scenario") does not change, so no path name does.
  - Renaming the Kotlin identifiers and the file (`jermainLonguenSlides`, `JermainLonguen.kt`) is optional.

<a id="issue-11"></a>
### ⬜ 11. "Decison" in 26 Moses slide titles, and lowercase verbs in four Jermain titles

- **Severity:** Low
- **Where:**
  - `content/Moses.kt:374-874`: the 4th through 29th "Decison" titles
  - `content/JermainLonguen.kt:695` ("Major Encounter: bluff Patrol"), `:752` ("fight 2nd Patrol"), `:1200`
    ("(via Ross): fight 1st Patrol"), and `:1217` ("bluff 1st Patrol")
- **Problem:** titles are the slide heading and the breadcrumb buttons on nearly every Moses slide, and they appear in
  the reasons page. A title is also part of every path name below it.
- **Fix:**
  - Correct the titles.
  - Add a migration that rewrites the stored values: e.g. `replace(..., 'Decison', 'Decision')` over
    `users.last_path_name` and `user_choices.from_path_name`, `to_path_name`, `from_title`, and `to_title`.
  - Rewrite both snapshots, and the example in `admins/slide-decks.md:22`.

<a id="issue-12"></a>
### ⬜ 12. Typos and grammar errors in both decks' text

- **Severity:** Low
- **Where and what** (body and choice text only, so no path names change):
  - `JermainLonguen.kt`:
    - `:47` "$10 dollars"
    - `:59`, `:1271`, `:1399` "business and hub-bub" (busyness and hubbub)
    - `:72` "**Three** the" (a missing comma)
    - `:125` "sound … follow"
    - `:137-140` "come along", "take advantage the", "on outskirts"
    - `:145-147` "turn romance", "It seem"
    - `:231` "take initiate", "for slave"
    - `:273-274` and `:289-290` "across back", "along side"
    - `:304-305` "following by an hot", "and and"
    - `:326` and `:560` "other ride"
    - `:359` "convince … you are purpose"
    - `:381` "riff raff"
    - `:403` "hut dwelling"
    - `:478` "women"
    - `:536` "bespeckled"
    - `:589-590` "strikes as", "frozen Ohio"
    - `:894` "Head into back into town"
    - `:1168` a leading "." in ".Try another building"
  - `Moses.kt`:
    - `:14` "Nancy was" (a missing comma)
    - `:18` "name Mr. Gooch"
    - `:21` "runaway"
    - `:31` "a exhausting"
    - `:49` "close the road"
    - `:61` "come to river"
    - `:108` "your live"
    - `:119` and `:219` "the gaols"
    - `:145` "switch a whack"
    - `:156` "rifle but"
    - `:191` "about to swept"
    - `:201` "some approaching"
    - `:217` "you friend"
    - `:223` "rest of your life is his"
    - `:245` "live is tolerable"
    - `:252` "bearable from"
    - `:255` "owners house"
    - `:267` "chances … is"
    - `:285` "with in"
    - `:293` "The greet"
    - `:315` "your were"
    - `:316` "let's you"
    - `:346` "approaches you offers"
    - `:463` "you are fugitive"
    - `:701` "Head the warning" (heed)
    - `:749` "stab on of"
    - `:817` "passers by"
    - `:886`: a trailing `\n` in a choice's text, which is stored in `choice_text` and shown in the reason dialog
    - `:325`, `:338`, `:378` "SS Fox": "SS" means steamship, but the text describes a sailing ship
- **Fix:** correct the text; a content author should review each change.

<a id="issue-13"></a>
### ⬜ 13. Companions, horses, patrols, and seasons that do not match the student's path

- **Severity:** Low
- **Where:** `content/JermainLonguen.kt`
- **Problem:**
  - "You and your companion" (`:316`) appears on the path where the student told no one (`:1455`, then slides 4, 13,
    14, 20, and 23).
  - "the reins of your horses" (`:274`, `:290`) and "Having ridden" (`:366`), though no horse is ever acquired.
  - "Having successfully fought off two patrols now" (`:566`) is also reached after bluffing the first patrol.
  - The same season leads to different slides by path: slide 6's spring skips Spring Departure (`:1402-1405`), and
    slide 5's summer skips Summer Departure (`:1266-1269`). The TODO at `:1274` notes one of these.
  - The warm-clothes advice is given twice (`:73-78`).
  - "lone log cabin" (`:582`) against the choice "Approach the cottage?" (`:763`).
- **Fix:** reword the shared slides to suit every path that reaches them. Rerouting a season choice changes path
  names, which needs the same care as issue 6.

<a id="issue-14"></a>
### ⬜ 14. Every image's alt text is "Pic", and a few markup slips

- **Severity:** Low
- **Where:** both decks' `<img ... alt="Pic">`; `JermainLonguen.kt:165-170` (a `<p>` nested in a `<p>`), `:204`
  (`width:164;` with no unit), `:244` and `:571` (unused constants `WINTER_TRAVEL_END` and `TEXT55_…`), `:1413` (a stale
  TODO above a commented-out choice)
- **Problem:** a screen reader announces "Pic" for every illustration, so a student using one misses what the picture
  shows. The other slips have no visible effect today.
- **Fix:** describe each image in its `alt`, fix the markup, and delete the unused constants and the TODO.
  `SlideContentTest` could refuse an `alt` that is empty or "Pic".

<a id="issue-15"></a>
### ⬜ 15. The reasons page cannot show which wrong answer a Moses student chose

- **Severity:** Low
- **Where:** `Pages.kt:135`; the `history.user_reasons` view (`V003__...sql:43-57`); `DbmsTables.kt:64-73`;
  `website/history-walk/docs/teachers/student-reasons.md:12`
- **Problem:**
  - The page shows "From → To" by slide title. Every wrong Moses answer leads to a slide titled `Incorrect1`,
    `Incorrect2`, or `Incorrect3`.
  - So a teacher reading a reason cannot tell which choice it explains.
  - The choice text is stored (`user_choices.choice_text`), but the view leaves it out.
- **Fix:**
  - Add `choice_text` to the view in a new migration, then to `UserReasonsView`, `UserReason`, and the page, e.g. as
    its own column.
  - Extend `PagesTest`, and update `student-reasons.md`.

<a id="issue-16"></a>
### ⬜ 16. A failed RPC call sends the exception's message and class name to the browser

- **Severity:** Low
- **Where:** `ServiceImpl.kt` (every method); Kilua's `RpcServiceManager`, which answers any exception with
  `error = e.message` and `exceptionType = e.javaClass.canonicalName`
- **Problem:**
  - An unexpected exception in the RPC service reaches the browser with its message: an `ExposedSQLException`
    (the statement text), or `error("Missing uuid: …")` from `User.kt:205` or `DbmsTxs.kt:83`.
  - The client only logs it to the console, but any student can read the console.
- **Fix:** in `ContentService`, catch everything but `ServiceException` and `CancellationException`, log it with the
  user, and throw a `ServiceException` with a generic message. The client already shows a `ServiceException`'s
  message and reloads the student's slide. Add a test with a failing database call.

<a id="issue-17"></a>
### ⬜ 17. The class pages go by the role stored at sign-in, not the role the app uses

- **Severity:** Low
- **Where:** the views in `V003__teachers_see_student_view.sql:22-57` (`users.role = 'student'`); `User.kt:120`, `:132`;
  `AGENTS.md` ("The role is recomputed from the live `TEACHER_EMAILS` on every request, not taken from the stored row")
- **Problem:**
  - Everything else derives the role from `TEACHER_EMAILS`. The progress views filter on `users.role`, which changes
    only when the user next signs in.
  - That matters for an address that matches both rules: one on `TEACHER_EMAILS` that also starts with two digits.
- **Scenario:**
  - A student is added to `TEACHER_EMAILS` to help run a trial, and stays signed in for the day.
  - Until their next sign-in, their test walk is counted in the class summary as a student's.
  - Once they sign in again, their earlier moves vanish from it.
- **Fix:**
  - At startup, after the migration, set `users.role` from `TEACHER_EMAILS` for every user. The list is read once per
    process, so the stored role then always matches the one the app uses.
  - Or filter the class pages by the live list instead of the column.
  - Make `AGENTS.md` say which.

<a id="issue-18"></a>
### ⬜ 18. Sign-in messages name athenian.org whatever `SCHOOL_EMAIL_DOMAIN` says

- **Severity:** Low
- **Where:** `commonMain/.../SignInError.kt:18` (`School.DEFAULT_DOMAIN`); `jsMain/.../SignInDialog.kt:51` ("your
  Athenian Google account"); `admins/configuration.md:30`
- **Problem:** the docs present `SCHOOL_EMAIL_DOMAIN` as a setting, and the server honors it. But the refusal message
  and the dialog's instruction are compiled into the client with Athenian's name.
- **Fix:**
  - Let the client learn the domain from the server: `/auth/greeting` could answer with the domain as well, or the
    refusal redirect could carry it.
  - Or document the setting as Athenian-only.
  - Cover it in `SignInDialogTest`.

<a id="issue-19"></a>
### ⬜ 19. A reason typed as the session runs out is lost at sign-in

- **Severity:** Low
- **Where:** `MainPanel.kt:231-251` (`promptUserForReason`); `Security.kt:52`; `ClientUtils.kt:47-59`
- **Problem:**
  - When `provideReason` gets a 401, `Security.withAuth` shows the sign-in dialog and parks the call for good.
  - Signing in navigates to Google, so the reason the student typed is thrown away.
  - Sessions last 12 hours, so it happens to a student who signed in the evening before and finishes the next day.
- **Fix:**
  - Before a `SecurityException` leaves `promptUserForReason`, keep the draft in `sessionStorage`, keyed by the
    move's from and to paths.
  - When the reason dialog next opens for that move, start it from the draft. Clear the draft once it is saved.
  - Add a `ReasonDialogTest` case.

<a id="issue-20"></a>
### ⬜ 20. No security headers: `nosniff`, `frame-ancestors`, HSTS, `Referrer-Policy`

- **Severity:** Low
- **Where:** `Installs.kt:46` (`DefaultHeaders` with no headers added); `Pages.kt:78` (an inline `onsubmit`)
- **Problem:**
  - No response carries `X-Content-Type-Options: nosniff` or `Content-Security-Policy: frame-ancestors 'none'`.
  - Nor does any response carry `Strict-Transport-Security` (in production) or a `Referrer-Policy`.
  - `SameSite=Strict` already blunts CSRF and framing, so these are a second line, for a browser or extension that
    does not honor it and for any future escaping slip on the teacher pages.
- **Fix:**
  - Add the headers in `DefaultHeaders`, with HSTS only when `SessionConfig.isProduction`.
  - A full CSP for the teacher pages would first need the admin form's inline `confirm` moved out.
  - Check one header in `docker-smoke` and in a route test.

<a id="issue-21"></a>
### ⬜ 21. `make release` pushes a rebuilt image, and cannot resume after a failed GitHub release

- **Severity:** Low
- **Where:** `Makefile:182` (`_docker-push`), `:283`, and the `release` recipe (`:306-316`); `build.gradle.kts:56-62`
- **Problem:**
  - `make ci` builds and smoke-tests an image, then `_docker-push` runs `./gradlew jar` again. That stamps a new
    `BUILD_TIME`, recompiles, and re-webpacks.
  - buildx then rebuilds the image, `apk add --upgrade` included, and builds an arm64 variant that was never started
    anywhere.
  - Any difference between the two builds ships untested, e.g. npm resolution (issue 4).
  - If `gh release create` then fails (expired auth, the network), `:VERSION` and `:latest` are already on Docker
    Hub. A second `make release` is refused with "already on Docker Hub: bump the version", and the "Still by hand"
    steps are never printed.
- **Fix:**
  - Build the jar once and have `release` push that jar, through a push target without the `jar` step.
  - Optionally `--load` and smoke-test the native platform before `--push`.
  - Create the GitHub release as a draft before the push and publish it after, or add a `release-github` target that
    finishes an interrupted release.

<a id="issue-22"></a>
### ⬜ 22. The docs site ignores its lock file, and its workflow misses Makefile changes

- **Severity:** Low
- **Where:** `Makefile:327` and `:344` (`uv sync` without `--locked`); `.github/workflows/website.yml:10-21`
- **Problem:**
  - Plain `uv sync` silently re-locks when `website/pyproject.toml` and `uv.lock` disagree, so CI can publish to
    Pages with whatever versions resolve that day. The comments say `uv.lock` pins them.
  - The workflow's path filters leave out `Makefile`, so a change to `website-build` (dropping `--strict`, say) is
    never built in a pull request.
- **Fix:** `uv sync --locked` in `website-env` (and the line at `:344`), and add `Makefile` to both path lists.

<a id="issue-23"></a>
### ⬜ 23. `docker-smoke` can stop without cleanup or diagnostics, and `make db` can wait forever

- **Severity:** Low
- **Where:** `Makefile:207-236` (`docker-smoke`); `Makefile:87` (`make db`'s `until ... pg_isready` loop);
  `Makefile:374-387` (`railway-restart`)
- **Problem:**
  - Under `set -e`, a failing `docker network create`, either `docker run`, or a `status=$(curl ...)` (curl exits 7
    once the server has died) exits the recipe at once.
  - It does so without `fail()`'s log tail, and leaves the containers and the network behind.
  - `make db` waits without a bound. If the container crash-loops under `--restart unless-stopped` (say
    `DB_DATA_DIR` holds another major version's data), it prints nothing, forever.
  - *Unconfirmed:* `railway-restart` polls `/ping` as soon as `railway restart` returns. If the CLI returns before the
    old container stops, the first "pong" comes from the process being replaced.
- **Fix:**
  - `trap cleanup EXIT` in `docker-smoke`, with `|| fail "..."` on the commands that can fail.
  - Bound the `pg_isready` loop as `docker-smoke` does, and print `docker logs` when it gives up.
  - For `railway-restart`, wait for the deployment's status (or for `/ping` to fail first) before waiting for it to
    answer.

<a id="issue-24"></a>
### ⬜ 24. The app's user owns the jar it runs, and the Gradle wrapper has no checksum

- **Severity:** Low
- **Where:** `Dockerfile:14` (`chown -R historywalk /app`) and `:23` (`COPY --chown=...`);
  `gradle/wrapper/gradle-wrapper.properties`; `Makefile:365` (`upgrade-wrapper`)
- **Problem:**
  - The server process can rewrite `/app/history-walk.jar`, though it needs no write access there: logback writes to
    stdout. Code execution in the server could therefore survive a container restart.
  - The wrapper downloads Gradle without a `distributionSha256Sum`, and `upgrade-wrapper` does not add one.
- **Fix:**
  - Copy the jar owned by root and drop the `chown`.
  - Pass `--gradle-distribution-sha256-sum` in `upgrade-wrapper`, and add the sum for the current version.

<a id="issue-25"></a>
### ⬜ 25. `AGENTS.md`, comments, and the Makefile have drifted from the code

- **Severity:** Low
- **Where and what:**
  - **Start command and port:**
    - `AGENTS.md:326-327` says a deployment starts with `java -jar build/libs/history-walk.jar -port=$PORT`.
    - But both Railway services run the Docker image (`README.md:158`, `Makefile:41-42`), which has no `build/libs`,
      and its `ENTRYPOINT` passes no `-port`.
    - `application.yaml:3` hard-codes 8080 with no `PORT` fallback, so the image works only while Railway targets
      8080. Ktor's YAML takes `port: "$PORT:8080"`.
  - **`make ci`:** `Makefile:149-153` and `AGENTS.md` say it runs every CI gate but Trivy. It leaves out the website
    workflow's `make website-build`.
  - **Heroku:** `build.gradle.kts:50` still names Heroku (removed in #29), and so does the comment at `App.kt:41-42`.
  - **Gradle deprecations:**
    - `build.gradle.kts:22-24` blames only Kover for the deprecation warning, yet KSP 2.3.12 is held back and also
      calls `setVisible`.
    - The status of issue 24 in the 2026-10-31 review says Kotlin 2.4.21 and KSP 2.3.13. But the catalog has 2.4.20
      and 2.3.12, and nothing there explains the rollback, so `make versions` keeps offering 2.4.21.
  - **Cloudflare:** `Makefile:510` says "see AGENTS.md" for a cached bundle, but AGENTS.md has no Cloudflare guidance.
  - **Stray tab:** `Makefile:295` has a literal tab in "nothing published".
  - **Security workflow:** `security.yml:20` says runs other than pull requests "are never cancelled". But master
    pushes, the schedule, and dispatches share one concurrency group, so GitHub cancels a pending run when a newer one
    queues.
- **Fix:** correct each one; add the `PORT` fallback to `application.yaml`.

<a id="issue-26"></a>
### ⬜ 26. The admin pages and `llms.txt` have drifted from the code

- **Severity:** Low
- **Where and what:**
  - `admins/slide-decks.md:31` and `:42-43` say a deck needs exactly one success slide, or it refuses to load.
    `SlideDeck.kt:61` only logs a warning for more than one.
  - `admins/docs-site.md:33-35` and `admins/development.md:72` describe what triggers the website workflow
    incompletely. `website.yml:10-14` also runs on `RELEASE_NOTES.md`, which the site shows through a symlink.
  - The "Docs" list in `llms.txt:36-56` leaves out `admins/docs-site.md` and the Release notes page.
- **Fix:** correct the pages and the list.

<a id="issue-27"></a>
### ⬜ 27. Dead code and stale comments

- **Severity:** Low
- **Where:**
  - **Unused code:**
    - `Dbms.dbQuery` (`Dbms.kt:150-155`)
    - `Property.ANALYTICS_ID` (`Property.kt:28-31`)
    - `User`'s database-reading constructor and the two `toUser` functions (`User.kt:24-48`, `:80-90`)
    - `DbmsTxs.MaxDeadEndDate` (`DbmsTxs.kt:193-195`)
    - the `PipelineCall` typealias and the commented-out `readonlyTx` (`Utils.kt:35-45`)
    - `SlideData.hasParents` and `hasOneParent` (`Dao.kt:37-41`)
  - **Used only by tests:** `User.deleteUser`, which also logs the user's address at INFO, and `User.userExists`.
  - **Commented-out code:** `Main.kt:49-50`, `Installs.kt:56-62`, `MainPanel.kt:289-295`
  - **Wrong comments:**
    - ~~The `@Suppress("unused")` at `EnvVar.kt:38`, on a function that is used.~~ Removed in Wave 1 (Step 3).
    - `Slide.kt:77`'s "Ignore children that are dead ends", which the code below it does not do.
  - **Misspelled names:** `wrongDecsions` (`DbmsTxs.kt:108`) and `slide10AutumDeparture`
- **Fix:** delete the unused and commented-out code, move the test-only helpers into the tests, and fix the comments
  and names.

<a id="issue-28"></a>
### ⬜ 28. The npm lock has 48 fixable HIGH or CRITICAL findings, all in build and test tools

- **Severity:** Low
- **Found:** in Wave 1, while doing Step 4, by `trivy fs --scanners vuln --severity HIGH,CRITICAL --ignore-unfixed` over
  `.kotlin-js-store/`
- **Where:** `.kotlin-js-store/yarn.lock`; the `lock-scan` job in `.github/workflows/security.yml`
- **Problem:**
  - Trivy finds 40 distinct vulnerabilities (47 HIGH, 1 CRITICAL), each with a fixed version, in 21 packages:
    - `minimist` 1.2.5 (CRITICAL, prototype pollution)
    - `qs` 6.9.6, `body-parser` 1.19.1, `follow-redirects` 1.14.6
    - `socket.io-parser` 4.2.4, `engine.io` 6.6.2, `ws` 8.17.1, `ua-parser-js` 0.7.31
    - `serialize-javascript` 6.0.2
    - `minimatch` 3.0.4 and 9.0.5, `brace-expansion` 1.1.11 and 2.0.1, `braces` 3.0.2, `picomatch` 2.3.0
    - `fast-uri` 3.0.5, `flatted` 3.2.5, `js-yaml` 4.1.0, `lodash` 4.17.21, `source-map-js` 1.0.2, `tmp` 0.2.1
  - They come with Karma (the client test runner and its local server) and webpack's tooling. Those run in CI and on
    the developer's machine, on input the repository controls.
  - None is known to be in the bundle students load, but that has not been checked package by package.
  - While they stand, `lock-scan` cannot fail the build. A new vulnerability in a package that does ship would be
    reported, not stopped.
- **Fix:**
  - Find which of the packages webpack puts in `main.bundle.js` (its stats output), and fix those first.
  - Take newer Kotlin and KVision releases where they bring newer Karma and webpack. Pin the transitive ones with Yarn
    resolutions in `build.gradle.kts` (the Yarn root extension's `resolution(...)`), then `make yarn-unlock` and
    commit the lock.
  - Once Trivy is clean, give `lock-scan`'s first step `exit-code: '1'` and add `lock-scan` to `RELEASE_CHECKS` in
    the Makefile.

## Suggested order of work

The steps run roughly highest-value first. Steps in the same wave can be done in parallel, by different people or in
separate branches, because they touch different files. Where two steps share a file, the order between them is noted.

Three rules apply throughout:

- **Content steps** need the content author: the code change is small, but the replacement text is theirs.
- **Migrations** are numbered in merge order. Steps 7, 8, 9, and 15 may each add one, so whoever merges later takes
  the next free `V00N`.
- **Docs:** a step that changes something a teacher or an administrator would notice updates its docs page in the same
  pull request, as `AGENTS.md` asks. Steps 20 and 21 cover only the drift that is already there.

### Wave 1: stop the bleeding (start all four at once)

| Step | Status | Issues | Work | Files |
|------|:------:|--------|------|-------|
| 1  | ✅ | [1](#issue-1) | Confirm the Heroku apps and databases are gone (then close this and the old issue 2), or rotate | none (the two review docs) |
| 2  | ✅ | [2](#issue-2) | 5xx instead of 404, logged at ERROR; 400 for bad parameters; a `/health` that checks the database; tests | `Installs.kt`, `Routes.kt`, tests, `troubleshooting.md` |
| 3  | ✅ | [3](#issue-3) | Check the interval and boolean settings at startup; floor the interval on both sides; tests | `EnvVar.kt`, `ServerVersionCheck.kt`, `Main.kt`, `VersionCheckState.kt`, `configuration.md` |
| 4  | ✅ | [4](#issue-4) | Commit the Yarn lock, fail on drift, and have Trivy scan it | `.gitignore`, `build.gradle.kts`, `Makefile`, `security.yml` |

The four steps share no files. Step 1 is a check in two dashboards, not code.

### Wave 2: content fixes that keep every path name (start now, alongside Wave 1)

| Step | Status | Issues | Work | Files |
|------|:------:|--------|------|-------|
| 5  | ⬜ | [5](#issue-5), [8](#issue-8), [9](#issue-9), [12](#issue-12) (Moses) | Slide 14's third choice and the duplicate-choice guard; upstream and downstream; the mother; Moses typos | `Moses.kt`, `Slide.kt`, `SlideDeckTest.kt`, `slide-paths/moses.txt`, `docs/index.md` |
| 6  | ⬜ | [10](#issue-10), [12](#issue-12) (Jermain), [13](#issue-13) (text only), [14](#issue-14) | Loguen; Jermain typos; continuity rewording; alt text and markup | `JermainLonguen.kt`, `Moses.kt` (alt text), `README.md`, `docs/index.md` |
| 7  | ⬜ | [7](#issue-7) | `leadsToDeadEnd` for the recorded `dead_end`; optionally fix the stored rows; tests | `Slide.kt`, `ServiceImpl.kt`, `DbmsTxs.kt`, `ContentServiceTest.kt`, optional migration |

- Steps 5 and 6 edit different decks, except the alt text in `Moses.kt`. Do that part in Step 5, or after it.
- Steps 5 and 7 both edit `Slide.kt`, in different functions. Merge the second on top of the first.
- Both 5 and 6 edit `docs/index.md`, one line each.

### Wave 3: content fixes that change path names (after Wave 2)

| Step | Status | Issues | Work | Files |
|------|:------:|--------|------|-------|
| 8  | ⬜ | [6](#issue-6), [13](#issue-13) (rerouted seasons) | Send "No" off the Ross path; a patrol that suits each path; a migration for the moved paths | `JermainLonguen.kt`, a migration, `slide-paths/jermain.txt` |
| 9  | ⬜ | [11](#issue-11) | Fix the titles; a migration that rewrites stored path names and titles | `Moses.kt`, `JermainLonguen.kt`, a migration, both snapshots, `slide-decks.md` |

- Step 8 follows Step 6, which edits the same file. Step 9 follows Steps 5 and 8, since all three edit the same titles
  and snapshots. Having one person do 8 and 9 avoids two rounds of snapshot rewrites.
- Deploy these when no class is mid-walk if you can. The migrations keep progress, but a page left open during the
  deploy will hold the old path names until it reloads.

### Wave 4: server and client hardening (any time, alongside Waves 2 and 3)

| Step | Status | Issues | Work | Files |
|------|:------:|--------|------|-------|
| 10 | ⬜ | [16](#issue-16) | Generic `ServiceException` for unexpected RPC failures; a test | `ServiceImpl.kt`, `ContentServiceTest.kt` |
| 11 | ⬜ | [17](#issue-17) | Sync `users.role` from `TEACHER_EMAILS` at startup; fix the `AGENTS.md` wording; a test | `User.kt` or `Dbms.kt`, `Main.kt`, `AdminTest.kt`, `AGENTS.md` |
| 12 | ⬜ | [18](#issue-18) | The configured domain in the sign-in dialog and refusal message; tests | `SignInError.kt`, `SignInDialog.kt`, `AuthRoutes.kt`, `SignInDialogTest.kt` |
| 13 | ⬜ | [19](#issue-19) | Keep a draft reason across a sign-in; a test | `MainPanel.kt`, `ReasonDialogTest.kt` |
| 14 | ⬜ | [20](#issue-20) | Security headers; check one in `docker-smoke` and a route test | `Installs.kt`, `Makefile`, tests |
| 15 | ⬜ | [15](#issue-15) | `choice_text` in the reasons view and page; tests; docs | a migration, `DbmsTables.kt`, `DbmsTxs.kt`, `Pages.kt`, `PagesTest.kt`, `student-reasons.md` |

- Step 10 follows Step 7, since both edit `provideReason`.
- Step 14 follows Step 2 (`Installs.kt`), and coordinates its `docker-smoke` change with Step 18.
- Step 11 and Step 3 both touch `Main.kt`, in different places.
- The rest are independent.

### Wave 5: build, release, and ops (any time)

| Step | Status | Issues | Work | Files |
|------|:------:|--------|------|-------|
| 16 | ⬜ | [21](#issue-21) | Push the jar `make ci` tested; a release that can be finished after a failure | `Makefile` |
| 17 | ⬜ | [22](#issue-22) | `uv sync --locked`; `Makefile` in the website workflow's paths | `Makefile`, `website.yml` |
| 18 | ⬜ | [23](#issue-23) | `trap cleanup EXIT` in `docker-smoke`; a bounded wait in `make db`; a surer `railway-restart` | `Makefile` |
| 19 | ⬜ | [24](#issue-24) | A root-owned jar in the image; a checksum for the Gradle wrapper | `Dockerfile`, `gradle-wrapper.properties`, `Makefile` |
| 23 | ⬜ | [28](#issue-28) | Upgrade or pin the flagged npm packages; then make `lock-scan` a gate and a release check | `build.gradle.kts`, `.kotlin-js-store/yarn.lock`, `security.yml`, `Makefile` |

- Steps 16, 17, 18, and 19 all edit the Makefile, in different targets, so one person can take the wave, or the steps
  can merge one after another.
- Step 16 is best done after Step 4, so the jar it pushes was built from the committed lock.
- Step 23 was added after Wave 1 (issue 28, found in Step 4). It can start any time; it edits `RELEASE_CHECKS` in the
  Makefile, away from the targets Steps 16–19 change.

### Wave 6: docs and cleanup (last)

| Step | Status | Issues | Work | Files |
|------|:------:|--------|------|-------|
| 20 | ⬜ | [25](#issue-25) | Correct `AGENTS.md`, the comments, and the Makefile messages; the `PORT` fallback | `AGENTS.md`, `Makefile`, `build.gradle.kts`, `App.kt`, `security.yml`, `application.yaml` |
| 21 | ⬜ | [26](#issue-26) | Correct the admin pages and `llms.txt` | `website/history-walk/docs/admins/*.md`, `llms.txt` |
| 22 | ⬜ | [27](#issue-27) | Delete the dead code; fix the comments and names | `User.kt`, `DbmsTxs.kt`, `Dbms.kt`, `Property.kt`, `Utils.kt`, `Dao.kt`, `EnvVar.kt`, and others |

- Steps 20 and 21 can run in parallel.
- Step 22 goes last, since it touches files that most earlier steps edit.

### Parallel tracks at a glance

| Track | Steps | Can start |
|-------|-------|-----------|
| Ops | 1 | done |
| Server errors and headers | 2 → 14 | now (Step 2 is done) |
| Settings | 3 | done |
| Supply chain | 4 → 16, 19, and 23 | now (Step 4 is done) |
| Moses content | 5 → 9 | now (with the content author) |
| Jermain content | 6 → 8 → 9 | now (with the content author) |
| Walk data | 7 → 10 | now |
| Hardening | 11, 12, 13, 15 | now |
| Build and CI | 17, 18 | now (one person, the Makefile) |
| Docs and cleanup | 20, 21 → 22 | after the steps above |
