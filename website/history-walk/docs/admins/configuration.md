# Configuration

The server is configured entirely through environment variables. A host's settings page (Heroku's *Config Vars*,
Railway's *Variables*) sets them; locally they come from `.env` (see [Development](development.md)). The server reads
them once, at startup, so a change takes effect when the server restarts. Heroku and Railway restart it by
themselves when a variable changes.

## Required

The server refuses to start without these.

| Variable | Value |
|---|---|
| `GOOGLE_CLIENT_ID` | The OAuth client ID from Google Cloud, `….apps.googleusercontent.com`. See [Google sign-in](google-sign-in.md). |
| `GOOGLE_CLIENT_SECRET` | That client's secret. |
| `OAUTH_CALLBACK_URL` | Where Google sends the browser back after sign-in: the deployment's public address followed by `/auth/callback`, such as `https://<app>.herokuapp.com/auth/callback`. It must be **`https`** for a deployed server, and must match an authorized redirect URI of the client exactly. |
| `SESSION_SECRET` | The key for the session cookie and the sign-in state, at least 16 characters. Required whenever `OAUTH_CALLBACK_URL`'s host is not `localhost`, `127.0.0.1`, or `::1`. Generate one with `openssl rand -hex 32`. |

!!! warning "`SESSION_SECRET`"

    Use a different secret for each deployment, and keep it out of the repository. Changing it signs everyone out
    (their walks are kept). Locally, with a `localhost` callback, the server falls back to a fixed development
    secret and logs a warning; never rely on that anywhere else.

## The walk and who may use it

| Variable | Default | Value |
|---|---|---|
| `SLIDES_VARIABLE_NAME` | `jermain` | The deck this deployment serves: `jermain` or `moses`, in any case. Any other value stops the server at startup with an error naming it. |
| `TEACHER_EMAILS` | none | The teachers' addresses, separated by commas, in any case: `ateacher@athenian.org,another@athenian.org`. Only these accounts get the teacher view. Empty means no teachers. See [Teachers and students](access.md). |
| `SCHOOL_EMAIL_DOMAIN` | `athenian.org` | The Google Workspace domain whose accounts may sign in. |
| `LOGIN_GREETING` | `History Walk` | The title of the sign-in dialog. |

## Features

| Variable | Default | Effect |
|---|---|---|
| `SHOW_RESET_BUTTON` | `false` | `true` gives teachers a **reset** link that deletes their own walk. Students never get one. |
| `ALLOW_SLIDE_ACCESS` | `false` | `true` lets teachers open `/slides`, the list of every slide, and jump their own walk to any slide (`/slide/<id>/<instance>`). |
| `DISPLAY_CONSECUTIVE_CORRECT_DECISIONS` | `true` | `false` hides the *Consecutive Correct Decisions* line on everyone's page. |

## Database

On Heroku, the Postgres add-on sets `DATABASE_URL`, and the server uses it in place of the `DBMS_*` variables.
Elsewhere, set the `DBMS_*` variables.

| Variable | Default | Value |
|---|---|---|
| `DATABASE_URL` | none | `postgres://user:password@host:port/database`, as Heroku sets it. When present, it wins over the three below. A password containing `@` must be percent-encoded. |
| `DBMS_URL` | `jdbc:pgsql://localhost:5432/historywalk` | A JDBC URL for the [pgjdbc-ng](https://impossibl.github.io/pgjdbc-ng/) driver: note **`jdbc:pgsql:`**, not `jdbc:postgresql:`. |
| `DBMS_USERNAME` | `postgres` | The database user. |
| `DBMS_PASSWORD` | `docker` | That user's password. |
| `DBMS_DRIVER_CLASSNAME` | `com.impossibl.postgres.jdbc.PGDriver` | The JDBC driver class. Leave it alone. |

The server logs a description of the database it connects to, never the password.

## Update checks

An open page asks the server every minute whether a newer version has been released (see
[Releases and updates](releases.md)).

| Variable | Default | Value |
|---|---|---|
| `CLIENT_VERSION_CHECK_INTERVAL_SECS` | `60` | How often an open page checks, in seconds. |
| `CLIENT_RELOAD_WARNING_INTERVAL_SECS` | `60` | How long an out-of-date page shows its update dialog before it counts down and reloads itself, in seconds. |

## The port

The server listens on the port given with `-port=` on its command line; the Procfile passes Heroku's `$PORT`
(`java -jar build/libs/history-walk.jar -port=$PORT`). The Docker image listens on 8080.

## Heroku build setting

| Variable | Value |
|---|---|
| `GRADLE_TASK` | `-Pprod=true jar`: what Heroku's Gradle buildpack runs to build the jar. |

## A complete example

A deployed Jermain walk, with placeholders for the secrets:

```bash
SLIDES_VARIABLE_NAME=jermain
GOOGLE_CLIENT_ID=1234567890-abcdefg.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=<from Google Cloud>
OAUTH_CALLBACK_URL=https://<jermain-app>.herokuapp.com/auth/callback
SESSION_SECRET=<openssl rand -hex 32>
TEACHER_EMAILS=ateacher@athenian.org,another@athenian.org
ALLOW_SLIDE_ACCESS=true
LOGIN_GREETING=History Walk
# DATABASE_URL is set by the Heroku Postgres add-on; elsewhere set DBMS_URL, DBMS_USERNAME, and DBMS_PASSWORD
```
