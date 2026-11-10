# Development

How to run History Walk on your own machine, change it, and check a change the way CI will.

## What you need

- **JDK 25**. The Gradle wrapper picks the toolchain (`gradle/gradle-daemon-jvm.properties`).
- **Docker**, for the local database and for the JVM tests, which start their own Postgres with Testcontainers.
- **Chrome**, for the page's tests, which run in headless Chrome.
- A **Google OAuth client** with `http://localhost:8080/auth/callback` and `http://localhost:3000/auth/callback` as
  redirect URIs (see [Google sign-in](google-sign-in.md)).

## Local settings

Put your settings in `.env` at the top of the repository. It is gitignored; the IntelliJ run configurations and
`make docker-run` read it.

```bash
GOOGLE_CLIENT_ID=….apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=…
OAUTH_CALLBACK_URL=http://localhost:3000/auth/callback
DBMS_URL=jdbc:postgresql://localhost:5432/jermain
TEACHER_EMAILS=you@athenian.org
SLIDES_VARIABLE_NAME=jermain
ALLOW_SLIDE_ACCESS=true
```

With a `localhost` callback, `SESSION_SECRET` may be left out: the server uses a fixed development secret and logs a
warning. The database defaults (`postgres` / `docker`) match `make db`.

`OAUTH_CALLBACK_URL` must match the port your browser uses: `3000` when you browse the `make client` dev server, `8080`
when you run the server alone.

## Running it

```bash
make db        # Postgres 18 in Docker, with the jermain and moses databases
make server    # the server on :8080, rebuilding as you edit
make client    # the page on :3000, with hot reload, proxying the server's routes to :8080
```

Then open `http://localhost:3000` and sign in.

The dev server forwards `/auth`, the API, `/static`, and the teacher pages to the server
(`webpack.config.d/proxy.js`). A new server route the browser loads directly must be added there.

## Tests

| Command | Runs |
|---|---|
| `make tests` | The server's tests (Kotest, MockK, Testcontainers Postgres) and the page's tests (Kotest in headless Chrome). |
| `make js-tests` | The page's tests alone; no Docker needed. |
| `./gradlew jvmTest --tests 'com.pambrose.historywalk.AdminTest'` | One server spec. The page's tests always run whole. |
| `make coverage` | The tests, then Kover's coverage reports (`build/reports/kover/html/index.html`), and the coverage floor. |
| `make lint` / `make format` | Checks, or fixes, the Kotlin style. |
| `make ci` | Every CI gate but the image scan: lint, the workflow audit, all tests, the Docker build, and the image's smoke test. |

The JVM line coverage must stay at or above the floor in `build.gradle.kts` (90%); CI fails under it. Raise the floor
as coverage grows; never lower it to let a change through.

## Continuous integration

GitHub Actions runs these on every push to `master` and every pull request:

| Workflow | Checks |
|---|---|
| Tests | The server's tests with coverage and the floor, and the page's tests in Chrome. |
| Lint | `make lint`. |
| Docker Image | Builds the jar and the image, and runs the smoke test. |
| Security | Builds the image and scans it with Trivy (Alpine, the Java runtime, and every library in the jar), failing on a HIGH or CRITICAL vulnerability that has a fix. It also runs every Monday, since a newly published vulnerability changes nothing in the repository. The findings from `master` and the weekly run, including those with no fix yet, appear under **Security → Code scanning**. |
| zizmor | Audits the workflow files, when they change. |
| Website | Builds the docs site when `website/` or `llms.txt` changes; on `master`, publishes it to GitHub Pages. |

And these on their own:

| Workflow | Does |
|---|---|
| Dependency Graph | On each push to `master`, reports the Java libraries to GitHub, so Dependabot can raise security alerts for them. |
| Clean Up Workflow Runs | Every Monday, deletes the runs, and their logs, that are more than 30 days old, keeping each workflow's most recent run whatever its age, and every Trivy database saved in the Actions cache but `master`'s newest (about 1 GB each). **Run workflow** with *dry run* lists what it would delete. |

The Tests and Lint runs upload their reports as artifacts (test results, coverage, and lint reports), which you can
download from the run's page for 30 days; then they expire by themselves.

All actions are pinned to commit hashes, and Dependabot proposes their updates.

## Where things are

| Path | Holds |
|---|---|
| `src/commonMain` | What the server and the page share: the RPC interface (`Services.kt`), its data, the reason rule. |
| `src/jvmMain` | The server: routes, sign-in (`auth/`), the database (`dbms/`), the teacher pages (`Pages.kt`), the decks (`content/`). |
| `src/jsMain` | The page: `MainPanel.kt` (the walk), sign-in, the update check. |
| `src/jvmMain/resources/db/migration` | The Flyway migrations. |
| `AGENTS.md` | A detailed guide to the code, for people and coding agents. |

`AGENTS.md` in the repository goes deeper on every part: how moves are checked on the server, how sessions work, the
caching rules, and each test.
