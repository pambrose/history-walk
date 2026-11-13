# Changelog

All notable changes to History Walk are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed

- **Fixes from Codacy's analysis**, none of which a teacher or a student would notice:
  - Every public class and function has a KDoc comment; 164 had none.
  - The sign-in dialog's fetch of its title and domain lets a coroutine's cancellation through rather than taking it
    for a failed fetch.
  - A setting missing from `application.yaml` is found with `propertyOrNull`, not by catching every `Throwable`.
  - The webpack and Karma config fragments declare the `config` global that Kotlin/JS defines for them, which ESLint
    took for an undefined variable.
  - The troubleshooting guide's table of log lines shows *Created user …* in italics, not between stray asterisks,
    and the teacher guide's sign-in steps are headed "How to sign in" rather than repeating the page's title.
  - The header's three new-tab links share `ClientUtils.openInNewTab`, and every list in AGENTS.md has a blank line
    before it.

## [1.3.1] - 2026-11-13

One change to the page, which stops checking for a new version while nobody is using it (#42), and a correction to
the guide. No new settings and no database changes; `apiVersion` is unchanged.

### Changed

- **An open page checks for a new version only while someone is using it.** A page nobody has clicked, typed,
  scrolled, or moved the mouse in for 5 minutes, or one in a background tab or a minimized window, stops asking the
  server; the first thing anyone does in it, or bringing it back into view, has it ask at once, then every
  `CLIENT_VERSION_CHECK_INTERVAL_SECS` again. A forgotten tab used to ask every minute for good, and a signed-out one
  logged a `401 Unauthorized: POST /rpc/routeContentServiceManager5` each time. An out-of-date page nobody is using now
  shows its update dialog, and reloads, once someone comes back to it. The page alone changed: its interface with the
  server is the same, so open pages aren't asked to reload.

### Fixed

- The configuration and deployment guides said the server needs `-port=$PORT` and the image listens on 8080; since
  1.3.0 both listen on the host's `PORT` when it sets one, else 8080, and `-port=` wins over both.

## [1.3.0] - 2026-11-11

The fixes from a review of the whole repository, `docs/code-review-2026-11-11.md` (#34–#39): the walks' text and
paths, the class pages, failures and settings, sign-in, the security of the image and its dependencies, and the release
tooling. Three data migrations run at the first start; back up both databases first.

### Added

- **The reasons page has a *Choice* column**: the choice each reason explains, as its button read. Every wrong Moses
  answer leads to an *Incorrect* slide, so the From/To titles alone could not say which one a student chose. Migration
  `V006` adds `choice_text` to the `user_reasons` view.
- **A reason survives an expired sign-in.** The reason is kept in the tab (`sessionStorage`) while it is sent, so one
  typed as the 12-hour sign-in ran out is offered again, already typed, once the student has signed in and makes the
  same choice.
- `/health`, open to all: `ok` while the database answers a trivial query within 3 seconds, else a 503, for an uptime
  monitor. One query runs at a time and its answer is reused for 5 seconds. `/ping` is unchanged and stays the image's
  health check.
- `/auth/domain`, open to all: `SCHOOL_EMAIL_DOMAIN`, which the sign-in dialog now names, in its instruction and in
  the message for an account from another domain (they said Athenian and athenian.org whatever the setting).
- `/version` ends with when the server started (`Started: <instant>`).
- `make release-github` creates a version's GitHub release after checking that Docker Hub's image was built from the
  current commit; `make release` runs it last, and run alone it finishes a release that stopped after the image push.
- `make railway-versions`, which runs `make served-version` for each walk on Railway, showing the client bundle version
  each serves beside this checkout's version.
- Migrations `V004` and `V005`, data only (see Changed and Fixed), and `V006` (above).
- `DbmsMigrateTest` stores a student at every place either deck had before `V005` and requires each to find its slide
  after every migration, so a later rename that strands a place fails CI.
- `docs/code-review-2026-11-11.md`, the review: 28 numbered issues and the order they were fixed in.

### Changed

- **Settings are read strictly.** The boolean settings (`SHOW_RESET_BUTTON`, `ALLOW_SLIDE_ACCESS`,
  `DISPLAY_CONSECUTIVE_CORRECT_DECISIONS`) take only `true` or `false`, in any case. `CLIENT_VERSION_CHECK_INTERVAL_SECS`
  takes a whole number from 10 to 3600, and `CLIENT_RELOAD_WARNING_INTERVAL_SECS` one from 0 to 3600. Any other value
  stops the server at startup, naming the setting. Before, `1` or `yes` silently meant false, a typo in an interval
  failed every version check, and a 0 had every open page poll the server nonstop. A page also never waits less than 5
  seconds between checks.
- **A failed request is answered by what went wrong**: 400 for a bad parameter, 404 where there is nothing, 503 when
  the database is not answering, 500 for anything else, each 5xx logged at ERROR with the method and path. Every
  failure used to be a 404 logged at INFO, so with the database down a signed-in student's page was a bare
  "404 Not Found". `/slide/<id>` with an id that is not a number is a 400.
- **The server listens on the platform's `PORT`** when it sets one, else 8080 (`application.yaml`); a `-port=` argument
  still wins.
- **The stored role follows `TEACHER_EMAILS`.** It is brought into line at every startup, so the class pages, which
  count by it, count an address on the teacher list that also starts with two digits by the role the app uses. An
  address the rule no longer admits keeps its stored role.
- **Slide titles:** Moses's say "Decision", not "Decison", and four Jermain titles capitalize their verb ("Bluff",
  "Fight"). Migration `V005` moves the stored places and moves with them, so each student keeps their place.
- **The Jermain walk's paths:**
  - Refusing Ross's deal led to the slides that assume Ross's papers and pistols; it now leads to Old Ben's own advice.
    A student who was on that branch's via-Ross winter, which is gone, goes back to the branch's first slide (`V005`).
  - Leaving in the summer on Old Ben's advice via Ross, and in the spring on Old Ben's own advice, now go through the
    Summer and Spring Departure slides, as on every other path (`V005`).
  - The patrol that the town exits and the walk without Ross share says nothing of papers or pistols.
- `make release` pushes the jar `make ci` built and smoke-tested rather than a rebuild, and labels the image with its
  commit (`org.opencontainers.image.revision`).
- `make ci` also builds the docs site strictly, as the Website workflow does.
- `make docker-smoke` removes what it started however it ends and names the step that failed, and also checks
  `/health` and `nosniff`; `make db` gives up after a minute, with the container's log; `make railway-restart` waits
  for each walk's `/version` to show a new start time rather than taking the replaced process's answer.
- The docs site is built with `uv sync --locked`, and its workflow also runs when the Makefile changes.
- `make served-version` without `ORIGIN_URL` shows only the bundle `PUBLIC_URL` serves, as "The served bundle", and
  says nothing of Cloudflare; with `ORIGIN_URL` it compares Cloudflare's bundle with the origin's as before.
- The build fails when the client's resolved npm packages differ from the committed lock, or when there is no lock;
  after an npm dependency change, run `make yarn-unlock` and commit the lock.
- `make release-check` leaves out the check runs named Dependabot. Since the npm lock is committed, Dependabot's
  security updates try to update it and fail (it has no `package.json` beside it), and those runs, which are not CI,
  attach to master's commits and would have stopped every release.

### Fixed

- **Dead ends behind a *Continue*.** A decision whose next slide has only a *Continue* on to a dead end is stored as
  leading to a dead end (`Slide.leadsToDeadEnd`), so *Consecutive Correct Decisions* resets for it. Jermain's "Leave in
  the autumn" and "Leave in the spring", and entering a ramshackle hut, were counted as correct; migration `V004` marks
  the ones already stored.
- **Moses's 14th decision** has the third choice it was written with ("Try to break your irons with a rock right
  there, beside the sleeping guards"): its two wrong choices had the same text, so one silently replaced the other. A
  slide now refuses a second choice with the same text, so such a deck fails to load.
- **The walks' text**, with no saved place moved by it:
  - The Jermain title slide, the README, and the docs spell Loguen's name right (they said "Longuen").
  - Moses's opening no longer says he was sold with his mother, which the later slides contradict.
  - Two correct Moses choices led to slides that described the wrong answer (upstream for downstream; a place to wade
    for a bridge or boat).
  - About 50 typos and grammar errors in both decks, and "SS Fox", a steamship's prefix, for the sailing ship Fox.
  - Jermain slides shared by several paths no longer mention a companion or two fought patrols that a student on some
    of those paths never had; the winter-clothes advice is given once; "Approach the cottage?" now says "log cabin",
    as the slide does.
  - Every Jermain image has alt text that describes it, for screen readers, in place of "Pic"; a test refuses an empty
    or "Pic" alt.
- `AGENTS.md`, `llms.txt`, and the admin docs no longer describe a start command the Railway services don't use, a
  Website workflow narrower than it is, or a deck that needs exactly one success slide; build and workflow comments
  name the real sources of the Gradle deprecation warning and the real cancellation rule.

### Removed

- Dead code: `Dbms.dbQuery`, the never-read `historywalk.site.googleAnalyticsId` property, `User`'s instance API (its
  database-reading constructor, `toUser`, `deleteUser`, which logged the user's address, and `userExists`, with the two
  tests that tested only them), `DbmsTxs.MaxDeadEndDate`, the `PipelineCall` typealias, an unused `EntityID.toUuid`,
  `SlideData.hasParents` and `hasOneParent`, two unused Jermain text constants, and commented-out code.
- The stale `kotlin-js-store` line in `.gitignore`.

### Security

- A failure of the server's own in an RPC call (a database error, a missing user) no longer sends its message and
  class name to the browser, where the SQL or the user's id could be read in the console; the browser is told only
  that something went wrong, and the server logs it with the user.
- Every response carries `X-Content-Type-Options: nosniff`, `Content-Security-Policy: frame-ancestors 'none'`, and
  `Referrer-Policy: same-origin`, and in production `Strict-Transport-Security`.
- The client's npm dependencies are locked: `.kotlin-js-store/yarn.lock` is committed, so CI builds, tests, and scans
  the bundle a release ships. Resolving it afresh cleared 47 of the 48 HIGH and CRITICAL vulnerabilities Trivy found in
  it, all in build and test tools, and `serialize-javascript` is pinned to 7 for mocha (`build.gradle.kts`), which
  cleared the last. The Security workflow's new `lock-scan` job fails on a fixable HIGH or CRITICAL vulnerability in
  the lock, and `make release-check` requires it.
- Dependabot's three alerts on the lock are fixed: `diff` 8 (GHSA-73rr-hh4g-fpgx, low) and `glob` 14
  (GHSA-3g3m-2fjr-6pw4, medium) under mocha, and `minimatch` 11 (GHSA-g3hv-75wg-m249, medium) under mocha and
  karma-webpack, each by a path-specific Yarn resolution in `build.gradle.kts`, since each fix is a newer major than
  its parent asks for. Karma's own `glob` 7 and `minimatch` 3, which no advisory covers, are unchanged. Trivy finds
  nothing in the lock at any severity.
- The image's user owns neither `/app` nor the jar, so code running in the server could not rewrite it.
- The Gradle wrapper checks the distribution's SHA-256 (`distributionSha256Sum`); `make upgrade-wrapper` records the
  new one.
- `/health`, open to anyone, runs one query at a time and reuses its answer, so no number of calls can pile up threads
  or take the walk's connections.

## [1.2.1] - 2026-11-10

Nothing changes in the app: this release is the docs and the repository.

### Added

- The docs site has a Context7 chat widget on every page, a floating button that opens an assistant answering from the
  repository and its docs (`website/history-walk/overrides/main.html`).

### Changed

- The docs no longer describe a Cloudflare setup, since the walks are served from Railway directly: the caching notes
  speak of browsers and any cache in front of a walk, and the steps for checking and purging Cloudflare after a deploy
  are gone. `make served-version` remains in the Makefile for a deployment behind Cloudflare.

### Removed

- Leftover files that nothing uses: `heroku.md` (the old Heroku settings), `app.json` (Heroku's app manifest), and
  `.gettext.json` (KVision's translation extractor config, from the project template; the app has no translations).
- The `notes/` folder (manual steps for a local Postgres, which `make db` replaced) and two webpack configs from the
  project template, `handlebars.js` and `moment.js`, for libraries the client doesn't use.

## [1.2.0] - 2026-11-10

### Added

- `DBMS_QUERY_PATH`: with `DATABASE_URL`, the query string to use in place of the URL's, as `DBMS_NAME` replaces the
  database, such as `sslmode=require`.
- A **Security** workflow: Trivy scans the Docker image on pushes and pull requests to master and every Monday, failing
  on a HIGH or CRITICAL vulnerability that has a fix and reporting findings to code scanning. The weekly cleanup also
  deletes the old Trivy databases from the Actions cache.
- A **Release notes** page on the docs site, the same text as `RELEASE_NOTES.md` (a symlink), linked from the home
  page.

### Changed

- The server connects with the standard PostgreSQL JDBC driver (`org.postgresql`, pgjdbc), the one the tests already
  used, in place of pgjdbc-ng 0.8.9. `DBMS_URL` must start with `jdbc:postgresql:` (it was `jdbc:pgsql:`), and an
  `ssl.mode` parameter becomes `sslmode`; any other `DBMS_URL` stops the server at startup with the fix, and spaces
  around it are dropped. `DATABASE_URL` needs no change. The connection stays unencrypted unless the URL sets
  `sslmode`, as before: the server sets `sslmode=disable` where pgjdbc would default to `prefer`.
- `DATABASE_URL`'s query string reaches the driver, still percent-encoded, in place of being dropped, so a
  `?sslmode=require` on it now encrypts the connection. The logged description still leaves it out.
- `make release-check` (and so `make release`) also requires the Security workflow's `image-scan` to have passed for
  the release commit, the one CI gate `make ci` doesn't run.
- Gradle 9.9.1 (was 9.8.2), Exposed 1.6.1, Flyway 13.12.1, kotlin-logging 8.0.7, and MockK 1.14.12; the docs site
  builds with Zensical 0.1.1 and pymdown-extensions 13.0.
- Kotlin goes back to 2.4.20 (from 2.4.21), deliberately.

### Security

- The image no longer carries Netty 4.1.63, shaded inside pgjdbc-ng, in which Trivy found eight HIGH or CRITICAL
  vulnerabilities with fixes (the classes they name were left out of the shaded jar). Trivy finds no HIGH or CRITICAL
  vulnerability in the 1.2.0 image.

## [1.1.3] - 2026-11-04

### Changed

- The class summary and student reasons pages have titles: a *Class Summary* or *Student Reasons* heading, and the
  same in their browser tabs ("Class Summary · History Walk"), which they open in.

## [1.1.2] - 2026-11-04

### Added

- The Apache License, Version 2.0 (`LICENSE`).
- `make log`, which prints the last 100 lines of each walk's Railway log (`SERVICE=`, `LOG_LINES=`), and
  `make log-jermain` and `make log-moses`, which follow one walk's log like `tail -f` until Ctrl-C.

### Changed

- The README is reorganized: local development first (what to install, `.env`, the make targets), every setting in one
  table, the Gradle tasks by their current names, and the teacher pages in a section of their own.
- `make served-version` compares the bundle the origin (the deployment) serves with Cloudflare's, wherever it runs.

### Removed

- The Heroku make targets (`log`, `restart`, `creds`) and their `APP=` setting.

## [1.1.1] - 2026-11-04

### Added

- `make railway-restart`, which restarts the Jermain and Moses services on Railway with the Railway CLI, then waits
  for each walk to answer and shows its version; and `make railway-redeploy`, which redeploys both from the newest
  Docker Hub image after a release, then waits until each reports the new version.

### Changed

- A teacher's **class summary** and **student reasons** links open their pages in new tabs, so the walk stays
  open beside them.
- A teacher's **reset** asks for confirmation before it deletes the teacher's walk, as the admin page's clear does.

## [1.1.0] - 2026-11-04

The first release. It covers all the work since the repository began in September 2021: the classroom app of
2021–2025, and its rebuild in October and November 2026 (pull requests #2 to #17). The sections below compare it with
the original classroom app; [History](#history) at the end tells the story in order.

### Added

#### The walk

- Two choose-your-own-path walks through the history of slavery and escape, one per deployment, chosen with
  `SLIDES_VARIABLE_NAME`: **Jermain** (the default; the Underground Railroad scenario of Jarm Logue, later the
  Reverend Jermain W. Loguen) and **Moses**.
- A reason for every decision, in a dialog that needs at least five words (at most 2,000 characters), counts words as
  they are typed, pasted, or dictated, and keeps the draft if saving fails.
- The path so far, as buttons above each slide that go back to any earlier slide; go-back choices (`goBack(-n)`), such
  as *Try another building in town?*; slides with a single way forward that advance without asking for a reason.
- *Total Decisions* and *Consecutive Correct Decisions* on every page (the second can be hidden with
  `DISPLAY_CONSECUTIVE_CORRECT_DECISIONS=false`), and a bookmark on the choices already taken from a slide.
- Images on slides, served from the jar under `/static/images`.

#### Teachers

- **Class summary** (`/summary`): each student's decisions, whether they reached the success slide, and the slide they
  are on, fewest decisions first.
- **Student reasons** (`/reasons`): every reason every student gave, grouped by student, in the order given.
- **Admin page** (`/admin`): one switch for the whole deployment, **Teachers see student view**, under which every
  teacher's page is a student's and their moves count in the class pages like a student's; and **Clear teachers'
  trial walks**, which deletes every teacher's walk (and any counted move of a former teacher) after the browser asks
  to confirm. The page shows how many teacher moves the class pages count.
- A teacher's own walk is a test walk, marked *(your test walk, not counted in class progress)* and left out of the
  class pages.
- **Slide list** (`/slides`) and slide jump (`/slide/<id>/<instance>`), with `ALLOW_SLIDE_ACCESS=true`.
- A **reset** link that deletes the teacher's own walk, with `SHOW_RESET_BUTTON=true`.
- Header links to the class summary, the student reasons, the admin page, **help** (the teacher guide, in a new tab),
  and sign-out, last.

#### Sign-in

- Google sign-in for the school's accounts (`SCHOOL_EMAIL_DOMAIN`, `athenian.org`). The role comes from the address:
  an address in `TEACHER_EMAILS` is a teacher's, one whose local part starts with two digits is a student's, and
  anyone else is refused with a message saying why.
- Google's account chooser offers only school accounts and always appears, so a personal account isn't used by
  accident.
- A sign-in dialog titled by `LOGIN_GREETING` (default *History Walk*), over a school photo; a header with the user's
  photo or initials, name, and address.
- Sign-in lasts 12 hours, and signing out ends the account's sessions in every browser.

#### Operations

- Flyway migrations, run at every startup (`V001` schema, `V002` session versions, `V003` the student-view switch).
- A startup guard that refuses to migrate a database whose `history` tables survived without Flyway's history table,
  instead of letting `V001` drop them.
- An update check: an open page learns of a release that changes `apiVersion`, shows an *Application Update* dialog,
  and then reloads itself after a countdown, never while a reason is being written
  (`CLIENT_VERSION_CHECK_INTERVAL_SECS`, `CLIENT_RELOAD_WARNING_INTERVAL_SECS`).
- `/ping` and `/version` for health checks, and `/threaddump` for teachers.
- A single `DATABASE_URL` for the database, such as Railway's `${{Postgres.DATABASE_URL}}`, parsed safely (an `@`, `:`,
  or `+` in the password, a bad URL refused without echoing it), with `DBMS_NAME` to use another database on the same
  server. Its connection is not encrypted, as suits a platform's private network.
- A Docker image: Java 25 on Alpine, non-root, with a `/ping` health check, built with `make docker-build`, run with
  `make docker-run`, smoke-tested with `make docker-smoke`, and pushed for `linux/amd64` and `linux/arm64` with
  `make docker-push`.
- Makefile targets for the local database (`db`, `reset-jermain`, `reset-moses`, `stop-db`), development (`server`,
  `client`), checks (`tests`, `js-tests`, `coverage`, `lint`, `format`, `zizmor`, `ci`),
  dependencies (`versions`, `versions-all`), deploys (`served-version`), releases (`release-check`, `release`), and the
  docs (`website-serve`, `website-build`, `website-clean`, `upgrade-site`). `make` alone prints them.
- `make release-check`, which lists every reason a checkout can't be released, and `make release`, which runs every
  CI gate, pushes the image to Docker Hub, and creates the GitHub release from `RELEASE_NOTES.md`.
- `make served-version`, which checks that Cloudflare serves the bundle the deployment does.

#### Documentation

- A documentation site, built with Zensical in `website/history-walk` and published to GitHub Pages at
  <https://pambrose.github.io/history-walk/>: a guide for teachers (8 pages) and one for administrators (11 pages).
  The site also serves `llms.txt`.
- `AGENTS.md`, a guide to the code for people and coding agents; `CHANGELOG.md`, `RELEASE_NOTES.md`, and `llms.txt`.

#### Quality

- Tests: 319 JVM specs (Kotest, MockK, Ktor's test host, and Testcontainers Postgres) and 80 client specs (Kotest in
  headless Chrome), including sign-in end to end against a fake Google, every route's access rules, the teacher pages'
  escaping of what students type, and both decks walked from the root.
- `SlidePathsTest`, which pins every path name a student can reach to a snapshot, so a rename that would strand saved
  progress fails CI.
- A 90% floor on JVM line coverage (91.9% at release).
- GitHub Actions: tests, lint, the Docker image with its smoke test, a zizmor audit, the dependency graph, the docs
  site, and a weekly cleanup that deletes runs and their logs after 30 days, keeping each workflow's most recent run;
  test, coverage, and lint reports are kept for 30 days. Every action is pinned to a commit SHA. Dependabot for the
  actions, the Docker base image, and security fixes in the Java libraries.

### Changed

- **Google sign-in replaces usernames and passwords.**
- **Slide content lives in this repository** and is compiled into the jar. It used to be Kotlin fetched from a
  separate repository and evaluated at runtime.
- `SLIDES_VARIABLE_NAME` takes `jermain` or `moses`, in any case, and defaults to `jermain`. Any other value stops
  the server at startup (it used to fall back silently to the Moses walk).
- Moves are checked on the server: a move must start from the user's current slide and name one of its choices, and
  the server takes slide titles, choice text, and dead ends from the deck, not from the page. A repeated move, such as
  a double-click, is accepted once and changes nothing.
- The page stays usable after a failed request: it shows a message instead of freezing, buttons are disabled while a
  request is in flight, and a refused move reloads the slide the user is really on.
- The fat jar is `build/libs/history-walk.jar`, with no version in its name, so a version bump can't leave a
  deployment's start command pointing at a missing file. It shrank from 96 MB to 35 MB.
- Built with Java 25, Kotlin 2.4.21, Ktor 3.6, KVision 9.7 with Kilua RPC 0.0.46 (in place of KVision's remote
  services), Exposed 1.6, Flyway 13, and Gradle 9.8 with a version catalog. Before the rebuild it used Kotlin 2.1 and
  KVision 8.2.
- Sources moved to the `com.pambrose.historywalk` package; server configuration moved to `application.yaml`; versions
  come from `gradle.properties` through a generated `BuildConfig`.
- Every `"` in slide text is shown as written (it used to be shown as `'`).

### Removed

- Usernames and passwords.
- `/contentreset`, which fetched Kotlin from GitHub and evaluated it, along with the `SLIDES_REPO_*` settings and the
  Kotlin scripting dependencies.
- The JitPack repository and the separate content and slides libraries.
- The `Procfile` and `system.properties`: each deployment sets its own start command,
  `java -jar build/libs/history-walk.jar -port=$PORT`.

### Fixed

- Go-back choices returned to the slide's parent whatever their offset; they now go back the number of slides they
  name, and three Jermain go-backs that relied on the old behavior now land on the town.
- A double-click recorded a choice twice or failed on the database's unique constraint.
- The reason dialog's OK button stayed disabled for dictated or pasted text.
- One failed request froze the whole page, the update check included.
- Before the first successful request, any error showed the sign-in dialog; now only a real sign-out does.
- An update could reload a page without its dialog ever being shown, losing a reason being typed; and a page could
  reload itself in a loop while a cache still served the old bundle.
- Panels and dialogs were never disposed, so each slide leaked a little memory.
- The teacher's *test walk* note ran into the decision count.
- A Bootstrap error from an old webpack jQuery setting that broke under jQuery 4.

### Security

- The database password is no longer written to the logs.
- `/threaddump` is for teachers only; it was open to anyone.
- A stolen sign-in callback URL can't be completed by someone else: the sign-in state is bound to a random secret in a
  cookie, and the PKCE verifier is derived from that secret, not from the state in the URL.
- Sessions are encrypted, signed cookies checked against the database on every request, last 12 hours, and end in
  every browser at sign-out.
- Per-user responses (sign-in and sign-out, the class pages, the admin page, the reset) are sent
  `Cache-Control: no-store, private`, and a page restored from the browser's back/forward cache reloads, so Back after
  a sign-out on a shared computer shows nothing of the previous user.
- `/userreset` is a POST; a GET or HEAD only redirects home, since any link or prefetch can send one.
- What students type reaches the teacher pages as text, never as markup.
- The Docker base image is pinned to its digest, and the image upgrades `libexpat` to 2.10.0 for four HIGH CVEs.

### History

- **2021.** The first classroom version: a KVision page and a Ktor server, with Postgres. Students signed in
  with a username and password, explained each decision (a word limit came in November), and could go back up the
  path. The slide decks moved to their own repository, loaded and evaluated at runtime, and gained images, *continue*
  slides, copied slides, and go-back offsets. Admin pages listed the slides and summarized each student's decisions,
  last slide, and success, and a reset helped during development. A `1.0.0` tag of November 2021 marks this version.
- **2022.** Consecutive correct decisions, a bookmark for choices already taken, the student reasons page, several
  wrong choices per slide, Ktor 2.0, and build fixes.
- **2023–2025.** Dependency upgrades, database configuration from a single URL, and README updates.
- **2026.** The rebuild: Kilua RPC and Java 21, then Google sign-in on Java 25 with Flyway and the decks in the
  repository (#2, #3); Docker, Cloudflare-aware caching, and the update check (#4); the go-back fix, tests, lint, and
  CI (#5, #7); five waves of fixes from a code review of 24 issues (#9–#12, #14); the admin page's student view
  (#13); `make docker-push` and version 1.1.0 (#16); and the documentation site (#17).

[Unreleased]: https://github.com/pambrose/history-walk/compare/1.3.1...HEAD
[1.3.1]: https://github.com/pambrose/history-walk/compare/1.3.0...1.3.1
[1.3.0]: https://github.com/pambrose/history-walk/compare/1.2.1...1.3.0
[1.2.1]: https://github.com/pambrose/history-walk/compare/1.2.0...1.2.1
[1.2.0]: https://github.com/pambrose/history-walk/compare/1.1.3...1.2.0
[1.1.3]: https://github.com/pambrose/history-walk/compare/1.1.2...1.1.3
[1.1.2]: https://github.com/pambrose/history-walk/compare/1.1.1...1.1.2
[1.1.1]: https://github.com/pambrose/history-walk/compare/1.1.0...1.1.1
[1.1.0]: https://github.com/pambrose/history-walk/commits/1.1.0
