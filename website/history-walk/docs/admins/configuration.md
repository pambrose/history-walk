# Configuration

The server is configured entirely through environment variables. A host's settings page (Railway's *Variables*, for
example) sets them; locally they come from `.env` (see [Development](development.md)). The server reads them once, at
startup, so a change takes effect when the server restarts. Railway restarts it by itself when a variable changes.

## Required

The server refuses to start without these.

| Variable | Value |
|---|---|
| `GOOGLE_CLIENT_ID` | The OAuth client ID from Google Cloud, `….apps.googleusercontent.com`. See [Google sign-in](google-sign-in.md). |
| `GOOGLE_CLIENT_SECRET` | That client's secret. |
| `OAUTH_CALLBACK_URL` | Where Google sends the browser back after sign-in: the deployment's public address followed by `/auth/callback`, such as `https://<service>.up.railway.app/auth/callback`. It must be **`https`** for a deployed server, and must match an authorized redirect URI of the client exactly. |
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
| `SCHOOL_EMAIL_DOMAIN` | `athenian.org` | The Google Workspace domain whose accounts may sign in. The sign-in dialog names it (*Sign in with your … Google account*), as does the message for an account from another domain. |
| `LOGIN_GREETING` | `History Walk` | The title of the sign-in dialog. |

## Features

Each takes `true` or `false`, in any case; unset or blank means the default. Any other value, such as `1` or `yes`,
stops the server at startup with an error naming the variable.

| Variable | Default | Effect |
|---|---|---|
| `SHOW_RESET_BUTTON` | `false` | `true` gives teachers a **reset** link that deletes their own walk. Students never get one. |
| `ALLOW_SLIDE_ACCESS` | `false` | `true` lets teachers open `/slides`, the list of every slide, and jump their own walk to any slide (`/slide/<id>/<instance>`). |
| `DISPLAY_CONSECUTIVE_CORRECT_DECISIONS` | `true` | `false` hides the *Consecutive Correct Decisions* line on everyone's page. |

## Database

Set the `DBMS_*` variables. A single `DATABASE_URL`, as some hosts' Postgres services provide it, works too, and the
server then uses it in place of the `DBMS_*` variables.

| Variable | Default | Value |
|---|---|---|
| `DATABASE_URL` | none | `postgres://user:password@host:port/database`, such as Railway's `${{Postgres.DATABASE_URL}}`. When present, it wins over the three below. A password containing `@` must be percent-encoded. A query string (`?name=value&...`) is passed on to the [PostgreSQL JDBC driver](https://jdbc.postgresql.org/documentation/use/#connection-parameters), so its parameters are that driver's, such as `sslmode`. The connection is not encrypted unless it sets `sslmode=require` (or a stricter mode), as suits a platform's private network. |
| `DBMS_NAME` | none | With `DATABASE_URL`: the database to use in place of the URL's, on the same server with the same user, such as `jermain` when the URL names Railway's default database. Spaces around it are dropped. |
| `DBMS_QUERY_PATH` | none | With `DATABASE_URL`: the query string to use in place of the URL's, such as `sslmode=require`, written as in a URL (percent-encoded, `&` between parameters). Spaces around it and a leading `?` are dropped. |
| `DBMS_URL` | `jdbc:postgresql://localhost:5432/historywalk` | A JDBC URL for the [PostgreSQL JDBC driver](https://jdbc.postgresql.org/documentation/use/), which must start with `jdbc:postgresql:`; the server refuses any other at startup. Not encrypted unless it sets `sslmode=require` (or a stricter mode). Versions through 1.1.3 used the pgjdbc-ng driver, whose URLs begin `jdbc:pgsql:`: change `pgsql` to `postgresql`, and any `ssl.mode` parameter to `sslmode`. |
| `DBMS_USERNAME` | `postgres` | The database user. |
| `DBMS_PASSWORD` | `docker` | That user's password. |
| `DBMS_DRIVER_CLASSNAME` | `org.postgresql.Driver` | The JDBC driver class. Leave it alone. |

The server logs a description of the database it connects to, never the password.

## Update checks

An open page asks the server every minute, while someone is using it, whether a newer version has been released (see
[Releases and updates](releases.md)). A page left alone for 5 minutes, or out of view, stops asking until someone uses it
again.

| Variable | Default | Value |
|---|---|---|
| `CLIENT_VERSION_CHECK_INTERVAL_SECS` | `60` | How often an open page checks, in seconds: a whole number from 10 to 3600. |
| `CLIENT_RELOAD_WARNING_INTERVAL_SECS` | `60` | How long an out-of-date page shows its update dialog before it counts down and reloads itself, in seconds: a whole number from 0 (no dialog) to 3600. |

A value that isn't a whole number in its range stops the server at startup with an error naming the variable and the
range. Every open page checks at the first interval, so a small one would load the server; a page never checks more
often than every 5 seconds, whatever the server says.

## The port

The server listens on the host's `PORT` when it sets one (Railway does), else on 8080. A `-port=` on its command line
wins over both, so a start command of `java -jar build/libs/history-walk.jar -port=$PORT` does the same as one without
it. The Docker image follows the same rule: 8080 unless the host sets `PORT`.

## A complete example

A deployed Jermain walk, with placeholders for the secrets:

```bash
SLIDES_VARIABLE_NAME=jermain
GOOGLE_CLIENT_ID=1234567890-abcdefg.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=<from Google Cloud>
OAUTH_CALLBACK_URL=https://<jermain-service>.up.railway.app/auth/callback
SESSION_SECRET=<openssl rand -hex 32>
TEACHER_EMAILS=ateacher@athenian.org,another@athenian.org
ALLOW_SLIDE_ACCESS=true
LOGIN_GREETING=History Walk
DBMS_URL=jdbc:postgresql://<host>:<port>/<database>
DBMS_USERNAME=<user>
DBMS_PASSWORD=<password>
```
