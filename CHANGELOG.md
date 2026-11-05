# Changelog

All notable changes to History Walk are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- A **Release notes** page on the docs site, the same text as `RELEASE_NOTES.md` (a symlink), linked from the home
  page.

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
  Reverend Jermain W. Longuen) and **Moses**.
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

[Unreleased]: https://github.com/pambrose/history-walk/compare/1.1.3...HEAD
[1.1.3]: https://github.com/pambrose/history-walk/compare/1.1.2...1.1.3
[1.1.2]: https://github.com/pambrose/history-walk/compare/1.1.1...1.1.2
[1.1.1]: https://github.com/pambrose/history-walk/compare/1.1.0...1.1.1
[1.1.0]: https://github.com/pambrose/history-walk/commits/1.1.0
