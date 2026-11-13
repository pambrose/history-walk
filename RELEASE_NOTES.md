# Release notes

## History Walk 1.3.2 — 2026-11-13

A maintenance release: two corrections to the guide, fixes from a code-quality scan, and two library updates. Nothing
changes in the walk itself.

### For teachers

- **Nothing changes for you or your students.** The walk, the class pages, and the admin page work as in 1.3.1. The
  guide's *Signing in* page now heads its steps *How to sign in*.

### For administrators

- **No new settings and no database changes**, and the page's interface with the server is unchanged, so open pages
  aren't asked to reload: each picks up 1.3.2 at its next load. `make railway-redeploy` deploys both walks from the new
  image and waits until each reports 1.3.2.
- **Coming from 1.2.1 or earlier?** The *Before you deploy* steps in 1.3.0's notes still apply: back up both walks'
  databases, check the settings, and deploy outside class time.
- **The troubleshooting page's table of log lines** shows *Created user …* in italics; it used to show stray
  asterisks.

### For contributors

- **Codacy checks every pull request**, and fails one that adds any issue. Every public class and function now has a
  KDoc comment (164 were added), so a new one needs one too, in tests as well. The development page and AGENTS.md
  describe the check and how to read its findings (`codacy pull-request <number>`).
- `ClientUtils.openInNewTab` opens the header's class pages and help in a new tab, with `noopener`.
- The sign-in dialog's fetches let a coroutine's cancellation through, `Property` reads a missing setting with
  `propertyOrNull`, and the webpack and Karma config fragments declare their `config` global for ESLint.
- Codacy's count of issues on `master` fell from 545 to 355. The rest are deliberate or noise: Markdown in `docs/` and
  the release notes, repeated strings in tests and slide text, generic catches that are boundaries, and SQL Server
  rules applied to the Postgres migrations.
- Flyway 13.12.2 and Kotest 6.2.7.

**Full Changelog**: <https://github.com/pambrose/history-walk/compare/1.3.1...1.3.2>

## History Walk 1.3.1 — 2026-11-13

A small release: a page left open stops checking for updates while nobody is using it. Nothing changes in the walk
itself.

### For teachers

- **A page left open goes quiet.** A page nobody has used for 5 minutes, or one in a background tab, stops asking the
  server whether a new version is out. As soon as someone uses it again (a click, a key, a scroll, the mouse, or
  switching back to its tab), it asks at once. So if an update comes out while a page sits unused, its *Application
  Update* dialog appears when a student comes back to it, not while nobody is looking, and the page reloads about a
  minute later, as before. Nothing is lost: every move is already saved.

### For administrators

- **Fewer requests, quieter logs.** Every open page used to ask the server every minute for as long as it stayed open,
  and a signed-out one logged `401 Unauthorized: POST /rpc/routeContentServiceManager5` each time. Now only pages in
  use ask.
- **No new settings and no database changes**, and the page's interface with the server is unchanged, so open pages
  aren't asked to reload: each picks up 1.3.1 at its next load, and checks every minute until then.
  `make railway-redeploy` deploys both walks from the new image and waits until each reports 1.3.1.
- **Coming from 1.2.1 or earlier?** The *Before you deploy* steps in 1.3.0's notes still apply: back up both walks'
  databases, check the settings, and deploy outside class time.
- **The guide's port notes are corrected.** The server listens on the host's `PORT` when it sets one, else on 8080,
  and a `-port=` argument wins over both. The configuration and deployment pages said `-port=$PORT` was needed.

### For contributors

- `PageActivity` (`jsMain`) says whether someone is using the page: it is visible, and someone did something in it
  within `IDLE_AFTER` (5 minutes). `listenForActivity` feeds it the window's events. `VersionCheckLoop` waits on it
  before each check, after the interval, so checks stay at least an interval apart.
- `PageActivityTest` and four new `VersionCheckLoopTest` cases cover it, and `settle()` is shared in `TestPage.kt`.

**Full Changelog**: <https://github.com/pambrose/history-walk/compare/1.3.0...1.3.1>

## History Walk 1.3.0 — 2026-11-11

The fixes from a review of the whole app: corrected walks, a clearer reasons page, steadier sign-in, and safer
releases. Administrators: back up both databases and check the settings before deploying (below).

### For teachers

- **The reasons page shows the choice.** A new *Choice* column shows the choice each reason explains, as its button
  read. Every wrong Moses answer leads to an *Incorrect* slide, so before this you couldn't tell which one a student
  picked.
- **Corrected walks.**
    - The Jermain walk spells Loguen's name right, and its images describe themselves to screen readers.
    - The Moses opening matches the rest of the story: Moses was sold away from his mother.
    - Two correct Moses river choices led to text describing the wrong answer.
    - Moses's 14th decision had lost one of its three choices.
    - About 50 typos are fixed across both walks.
- **The Jermain walk's paths make sense.**
    - Refusing Ross's deal used to lead to slides about the papers and pistols Ross would have given; it now leads to
      Old Ben's own advice.
    - Leaving in the summer or spring now goes through the departure slide on every path.
    - The patrol slides no longer contradict what the student is carrying.
- **Students keep their place.** Slide titles were corrected ("Decision", not "Decison") and some Jermain choices
  rerouted; the update moves each student's saved place with them. The one exception: a student who refused Ross and
  was partway through the winter that followed goes back to choosing when to leave.
- ***Consecutive Correct Decisions* counts honestly.** Leaving in the autumn or spring, or entering a ramshackle hut,
  ends the attempt a slide later, after a *Continue*; these were counted as correct decisions, and now count as the
  wrong turns they are, including those already made. Some students' counts will drop.
- **A reason isn't lost to an expired sign-in.** If a student's sign-in (12 hours) runs out as they press **OK**, they
  sign in again, make the same choice, and find their reason already typed.
- **The sign-in dialog names your school's domain**, as the administrator set it.
- **When the server can't reach its database**, a page says so (*503 Service Unavailable: the database is not
  answering*) rather than *404 Not Found*.

### For administrators

- **Before you deploy:**
    - **Back up both walks' databases.** Three migrations run at the first start: `V004` marks the dead ends described
      above, `V005` moves saved places and moves to the corrected slide names, and `V006` adds the reasons page's
      *Choice* column.
    - **Check both services' settings.** `SHOW_RESET_BUTTON`, `ALLOW_SLIDE_ACCESS`, and
      `DISPLAY_CONSECUTIVE_CORRECT_DECISIONS` now take only `true` or `false`.
      `CLIENT_VERSION_CHECK_INTERVAL_SECS` takes a whole number from 10 to 3600, and
      `CLIENT_RELOAD_WARNING_INTERVAL_SECS` one from 0 to 3600. Any other value, such as `1` for a switch, stops the
      walk at startup with an error naming the setting; before, it was silently misread.
    - **Deploy outside class time.** A page left open across the deploy keeps the old slide names until it reloads:
      its first choice gets *That choice is not on your current slide* and shows the student's slide again, after
      which it works. The page's interface with the server is unchanged, so open pages aren't asked to reload.
- Then `make railway-redeploy` deploys both walks from the new image and waits until each reports 1.3.0.
- **New addresses:**
    - `/health` answers `ok` while the database answers, else a 503, for an uptime monitor.
    - `/auth/domain` serves `SCHOOL_EMAIL_DOMAIN` to the sign-in dialog.
    - `/version` ends with when the server started.
- **The server listens on the platform's `PORT`** when it sets one, else 8080.
- **Errors:** a failed request is a 400, 404, 500, or 503 by its cause, and each 5xx is logged at *ERROR* with the
  method and path. They were all 404s at *INFO*.
- **Teachers' stored roles follow `TEACHER_EMAILS`** at every start, so the class pages count each person by the role
  the app gives them now.
- **Security:**
    - Every response carries `nosniff`, `frame-ancestors 'none'`, and `Referrer-Policy: same-origin`, and HSTS in
      production.
    - A server error no longer shows its details in the browser.
    - The image's user can't rewrite the app's jar.
    - The page's npm packages are locked and scanned for vulnerabilities, like the image's libraries, and have no known
      vulnerability left at any severity.
- **Releases:**
    - `make release` pushes the image it just tested.
    - `make release-github` finishes a release that stopped after pushing its image.
    - `make railway-restart` waits until each walk has really restarted.
    - `make railway-versions` shows the version each walk serves.

### For contributors

- `docs/code-review-2026-11-11.md` records the review: 28 issues, and the waves that fixed them (#34–#39).
- **npm dependencies:** the lock (`.kotlin-js-store/yarn.lock`) is committed, and the build fails if it drifts. After
  an npm dependency change, run `make yarn-unlock` and commit the lock. The Security workflow's `lock-scan` fails on a
  fixable HIGH or CRITICAL vulnerability, and is a release check.
- **Renaming a slide:** `DbmsMigrateTest` puts a student at every place the decks had before `V005` and fails if one
  is stranded, so a rename needs a migration like `V005` (or a deliberate fixture edit).
- **New checks:**
    - A slide refuses two choices with the same text.
    - `SlideContentTest` refuses an image without real alt text.
    - `make ci` builds the docs site.
    - `make docker-smoke` checks `/health` and the security headers.
- **Gotcha:** adding a defaulted parameter to a function the JVM specs call can leave stale test classes in Gradle's
  build cache. `./gradlew compileTestKotlinJvm --rerun` fixes it (`AGENTS.md`).
- Dead code is gone, and the Gradle wrapper checks its distribution's checksum.
- `make release-check` ignores Dependabot's own update jobs. Dependabot can't update the npm lock, which has no
  `package.json` beside it, so those jobs fail on every master commit; they aren't CI.

**Full Changelog**: <https://github.com/pambrose/history-walk/compare/1.2.1...1.3.0>

## History Walk 1.2.1 — 2026-11-10

A release of the guide, not the app: nothing changes in the walk for teachers or students, or in how it is run.

### For teachers

- **Ask the guide a question.** Every page of the [guide](https://pambrose.github.io/history-walk/) has a blue chat
  button at the bottom right. It opens an assistant, run by Context7, that answers from this guide and the app's
  source. Check what it says against the pages it points to, and don't type students' names or other private details
  into it: what you type is sent to Context7.

### For administrators

- **No change to the app.** No new settings, no database changes, and the page's interface with the server is
  unchanged, so open pages aren't asked to reload. Deploying 1.2.1 is optional: `make railway-redeploy` deploys both
  walks from the new image and waits until each reports 1.2.1.
- **The guide no longer describes a Cloudflare setup**, since the walks are served from Railway directly. The caching
  notes speak of browsers and any cache in front of a walk. `make served-version` remains for a deployment behind
  Cloudflare.

### For contributors

- The docs site's template override, `website/history-walk/overrides/main.html` (`custom_dir` in `zensical.toml`),
  adds the chat widget to every page, in the theme's primary blue.
- Files nothing used are gone: `heroku.md`, `app.json`, `.gettext.json`, the `notes/` folder, and the
  `handlebars.js` and `moment.js` webpack configs from the KVision project template.

**Full Changelog**: <https://github.com/pambrose/history-walk/compare/1.2.0...1.2.1>

## History Walk 1.2.0 — 2026-11-10

A release for administrators: a new database driver, and an image checked for known vulnerabilities. Nothing changes
in the walk for teachers or students.

### For teachers

- **Release notes in the guide.** The [guide](https://pambrose.github.io/history-walk/) has a **Release notes** page,
  linked from its home page, with what each release changed.

### For administrators

- **Check `DBMS_URL` before you deploy.** The server now uses the standard PostgreSQL JDBC driver in place of
  pgjdbc-ng.
    - A walk that gets its database from `DATABASE_URL` (Railway's `${{Postgres.DATABASE_URL}}`) needs no change.
    - A walk that sets `DBMS_URL` needs it to start with `jdbc:postgresql:` in place of `jdbc:pgsql:`, with any
      `ssl.mode` parameter written `sslmode`. The server refuses any other `DBMS_URL` at startup, with a message that
      says what to change. A local `.env` needs the same change.
- **The connection stays unencrypted** unless its URL sets `sslmode=require` (or a stricter mode), as before, which
  suits a platform's private network.
- **`DBMS_QUERY_PATH`** is new: with `DATABASE_URL`, the query string to use in place of the URL's, such as
  `sslmode=require`. A `DATABASE_URL`'s own query string, which was dropped, now reaches the driver, so a
  `?sslmode=require` on it now encrypts the connection.
- **No known HIGH or CRITICAL vulnerabilities in the image.** The old driver bundled an old Netty in which a scan
  found eight, though the code they affect had been left out of the driver. Every image CI builds is now scanned, and
  again every week; findings appear under the repository's **Security → Code scanning**.
- No database changes, and the page's interface with the server is unchanged, so open pages aren't asked to reload.
  Once each walk's database settings are right, `make railway-redeploy` deploys both walks from the new image and
  waits until each reports 1.2.0.

See [Configuration](https://pambrose.github.io/history-walk/admins/configuration/#database).

### For contributors

- The app and the tests now use the same driver, pgjdbc (`org.postgresql`); `pgjdbc-ng-all` is gone.
  `Dbms.requirePgjdbcUrl` checks `DBMS_URL`, and `Dbms.unencryptedUnlessAsked` keeps pgjdbc from trying SSL first.
- The **Security** workflow (`security.yml`) builds the image and fails on a HIGH or CRITICAL vulnerability that has a
  fix. `make ci` doesn't run it, since it needs Trivy; CI does, and `make release-check` requires it to have passed
  for the release commit. The weekly cleanup also deletes old Trivy databases from the Actions cache.
- Gradle 9.9.1, Exposed 1.6.1, Flyway 13.12.1, kotlin-logging 8.0.7, and MockK 1.14.12; the docs site builds with
  Zensical 0.1.1. Kotlin goes back to 2.4.20, deliberately.

**Full Changelog**: <https://github.com/pambrose/history-walk/compare/1.1.3...1.2.0>

## History Walk 1.1.3 — 2026-11-04

The class pages are easier to find among your browser tabs.

### For teachers

- **The class pages have titles.** The class summary is headed **Class Summary** and the student reasons **Student
  Reasons**, and their browser tabs say so ("Class Summary · History Walk"), so you can tell them apart from your walk
  and from each other.

### For administrators

- No new settings and no database changes: `make railway-redeploy` deploys both walks from the new image and waits
  until each reports 1.1.3.

**Full Changelog**: <https://github.com/pambrose/history-walk/compare/1.1.2...1.1.3>

## History Walk 1.1.2 — 2026-11-04

A release for administrators and contributors; nothing changes for teachers or students.

### For administrators

- **`make log`** prints the last 100 lines of each walk's Railway log (`SERVICE=Moses` for one walk, `LOG_LINES=500`
  for more). **`make log-jermain`** and **`make log-moses`** follow one walk's log like `tail -f` until Ctrl-C.
- **The Heroku make targets are gone** (`log`, `restart`, `creds`, and their `APP=` setting): the walks run on
  Railway, where `make log`, `make railway-restart`, and `make railway-redeploy` cover them. `make served-version`
  still compares the bundle the deployment serves with Cloudflare's.
- No new settings and no database changes: `make railway-redeploy` deploys both walks from the new image and waits
  until each reports 1.1.2.

### For contributors

- History Walk is now licensed under the Apache License, Version 2.0 (`LICENSE`).
- The README is reorganized: local development first (what to install, an example `.env`, the make targets), every
  setting in one table with its default, the Gradle tasks by their current names, and the teacher pages in a section
  of their own.

**Full Changelog**: <https://github.com/pambrose/history-walk/compare/1.1.1...1.1.2>

## History Walk 1.1.1 — 2026-11-04

Two changes to the links at the top of a teacher's page.

### For teachers

- **The class pages open in new tabs.** **class summary** and **student reasons** now open beside your walk, so you
  can keep walking and refresh them to follow your class.
- **Reset asks first.** The **reset** link, shown only when your administrator turns it on, now asks you to confirm
  before it deletes your own walk, as the admin page's **Clear teachers' trial walks** does. Hover over it for a
  reminder of what it deletes.

### For administrators

- No new settings and no database changes: `make railway-redeploy` deploys both walks on Railway from the new image
  and waits until each reports 1.1.1.
- `make railway-restart` restarts both walks as they are, for a walk that is stuck.
- The page's interface with the server is unchanged, so pages already open aren't asked to reload; they take 1.1.1 at
  their next load.

**Full Changelog**: <https://github.com/pambrose/history-walk/compare/1.1.0...1.1.1>

## History Walk 1.1.0 — 2026-11-04

The first release of History Walk: a choose-your-own-path history scenario in which Athenian students make the
decisions of a person escaping slavery and explain each one, and teachers follow their reasoning. It rebuilds the
classroom app of 2021 on current tools, with school Google accounts, pages for teachers, and a guide for teachers and
administrators at <https://pambrose.github.io/history-walk/>.

### For teachers

- **Sign in with your school Google account.** No more usernames and passwords. Your address on the teacher list
  gives you the teacher view; students are recognized by their address. A sign-in lasts a school day (12 hours), and
  **logout** signs you out everywhere.
- **Follow your class.** The **class summary** shows each student's decisions, whether they reached freedom, and
  where they are now. **Student reasons** shows every reason every student gave, in order.
- **Try the walk as your students will.** On the new **admin** page, **Teachers see student view** turns every
  teacher's page into a student's and counts your moves in the class pages, for a trial run before class.
  **Clear teachers' trial walks** removes them all again. Your own walk is otherwise a test walk that never appears
  among the students'.
- **Students' reasons are safer to collect.** A reason needs at least five words; dictated and pasted text now count;
  and if saving fails, the reason is kept for another try instead of lost.
- **Fewer surprises in class.** A slow or failed click shows a message instead of freezing the page; a double-click
  records one decision; when the app is updated during class, the page offers to reload and never does while a
  student is typing.
- **Go-back choices work.** *Try another building in town?* now returns to the town, wherever it is offered.
- **Help is one click away.** The **help** link at the top of your page opens the teacher guide in a new tab.

See the [teacher guide](https://pambrose.github.io/history-walk/teachers/).

### For administrators

- **New settings.** Each deployment needs `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `OAUTH_CALLBACK_URL` (its
  `https` address followed by `/auth/callback`), `SESSION_SECRET` (`openssl rand -hex 32`, a different one per
  deployment), and `TEACHER_EMAILS`. `SLIDES_VARIABLE_NAME` must be `jermain` or `moses`. The
  [configuration reference](https://pambrose.github.io/history-walk/admins/configuration/) lists every setting.
- **Google sign-in setup.** Create an OAuth client (*Web application*, consent screen *Internal*) and add each
  deployment's callback as an authorized redirect URI, exactly, with `https`. See
  [Google sign-in](https://pambrose.github.io/history-walk/admins/google-sign-in/).
- **The database** can come from a platform's `DATABASE_URL`, such as Railway's `${{Postgres.DATABASE_URL}}`, with
  `DBMS_NAME` naming the walk's own database on that server; or from `DBMS_URL`, `DBMS_USERNAME`, and
  `DBMS_PASSWORD`. A `DATABASE_URL` connection is not encrypted, so keep it on the platform's private network.
- **The database migrates itself** at every start, and refuses to start, rather than delete anything, on a database
  whose tables survived without Flyway's history (a partial restore). Back up whole databases.
- **Runs anywhere Java 25 does**: built from the repository, on Railway, or from the Docker image (`make docker-build`,
  `make docker-push`).
- **Smaller and safer**: the jar is 35 MB (was 96 MB); the database password is out of the logs; per-user pages are
  never cached; `/threaddump` is for teachers only; and the runtime fetch-and-evaluate of slide code is gone.
- **Releases** are `make release-check`, which lists anything that stops a release, and `make release`, which runs
  every check, pushes the image, and creates the GitHub release. See
  [Releases and updates](https://pambrose.github.io/history-walk/admins/releases/).

See the [administrator guide](https://pambrose.github.io/history-walk/admins/).

### Upgrading from the 2021 version

1. **Start with a fresh database.** The 2021 version's users signed in with passwords and its tables don't match this
   release's, so its data isn't carried over. History Walk 1.1.0 won't start on a database the old version used:
   rather than drop the old tables, it stops with an error. Give each deployment a new, empty database, or back up the
   old one and drop its `history` schema (and `public.flyway_schema_history`, if it has one). The first start then
   builds the schema.
2. **Set up Google sign-in** for each deployment, and set the new variables above. The server won't start without
   the required ones.
3. **Set `SLIDES_VARIABLE_NAME`** to `jermain` or `moses`. The old values, such as `jermainLonguenSlides`, now stop the
   server at startup. Unset the old `SLIDES_REPO_*` variables.
4. **Set the start command** on a host that builds from the repository:
   `java -jar build/libs/history-walk.jar -port=$PORT`. The repository no longer has a Procfile or `system.properties`.
5. **Deploy**, then check `/version` and sign in as a teacher.
6. **Behind Cloudflare**, run `make served-version PUBLIC_URL=… ORIGIN_URL=…` and purge `main.bundle.js` if it
   reports a stale copy.
7. **Rotate the credentials of any database the old version used**: the old version wrote the database
   password to the logs.

### Known issues

- KSP is held at 2.3.12: KSP 2.3.13 can make the build fail with *The 'expect' declaration 'getService' has no
  'actual' declaration* ([google/ksp#3286](https://github.com/google/ksp/issues/3286)).
- The teacher pages (class summary, student reasons, admin) are plain pages; refresh them to see new moves.

**Full Changelog**: <https://github.com/pambrose/history-walk/commits/1.1.0> (every change since the repository
began), and [CHANGELOG.md](https://github.com/pambrose/history-walk/blob/master/CHANGELOG.md).
