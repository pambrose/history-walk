# Monitoring and troubleshooting

## Health checks

| Address | Answers | Use |
|---|---|---|
| `/ping` | `pong` | The server is up. It touches neither the database nor a session; the Docker health check uses it. |
| `/version` | `Version: 1.2.1 Release Date: … Build Date: …` | Which release a deployment runs. |
| `/threaddump` | Every JVM thread's stack (teachers only) | A hung or slow server. Taking it briefly pauses the JVM. |

```bash
curl -s https://<deployment>/ping
curl -s https://<deployment>/version
```

## Logs

- **Railway**: `make log` prints the last 100 lines of each walk's log (`LOG_LINES=500` for more, `SERVICE=Moses` for
  one walk). `make log-jermain` and `make log-moses` follow one walk's log like `tail -f`: its last 10 lines
  (`TAIL_LINES=` for more), then each new line within a few seconds (`FOLLOW_EVERY=` sets how often it checks), until
  Ctrl-C. Or the service's **Logs** tab.
- **Docker**: `docker logs <container>`.

Worth knowing in the log:

| Line | Means |
|---|---|
| *Serving the jermain slide deck (SLIDES_VARIABLE_NAME is …)* | Which walk this deployment serves. |
| *Container database: host:port/db as user* | The database it connected to through `DATABASE_URL` (never the password). |
| *Flyway applied N migration(s); schema version … -> …* | The migrations this start ran. |
| *Created user … * | Someone signed in for the first time. |
| *Signing out the session for user …: …* | A session was ended, with why: expired, signed out elsewhere, or no longer admitted. |
| *Teachers see the student view* / *teacher view* | Someone pressed the admin page's switch. |
| *Cleared N moves of M teachers* | Someone pressed **Clear teachers' trial walks**. |

## The server won't start

| Error | Fix |
|---|---|
| *Missing GOOGLE_CLIENT_ID value* (or `GOOGLE_CLIENT_SECRET`, `OAUTH_CALLBACK_URL`) | Set the variable. See [Configuration](configuration.md). |
| *SESSION_SECRET must be set for a server reached at …* | Set `SESSION_SECRET` (`openssl rand -hex 32`). |
| *SESSION_SECRET is too weak (min 16 chars)* | Generate a longer one. |
| An error naming `SLIDES_VARIABLE_NAME`'s value | It must be `jermain` or `moses`, or unset. |
| *Refusing to migrate: the history schema holds tables, but Flyway's schema history table … is missing or empty* | A restore left out `public.flyway_schema_history`. Restore it; see [the startup guard](database.md#the-startup-guard). |
| *DATABASE_URL has no host; an @ in the password must be percent-encoded* (and other *DATABASE_URL …* errors) | Fix the URL. |
| A Flyway *checksum mismatch* | An applied migration was edited. Put it back as it was, and make the change a new migration. |
| *DBMS_URL starts with jdbc:pgsql:, the scheme of the pgjdbc-ng driver, which History Walk no longer uses* | The URL was written for versions through 1.1.3. Change `jdbc:pgsql:` to `jdbc:postgresql:`, and any `ssl.mode` parameter to `sslmode`. See [Configuration](configuration.md#database). |
| *DBMS_URL must start with jdbc:postgresql:* | Write it as `jdbc:postgresql://<host>:<port>/<database>`. A host's `postgres://user:password@host:port/database` URL goes in `DATABASE_URL` instead. See [Configuration](configuration.md#database). |
| A connection error | The database is down or unreachable, or the `DBMS_*` settings are wrong. |

## Sign-in problems

See [Google sign-in](google-sign-in.md#other-sign-in-problems). The most common:

- **`Error 400: redirect_uri_mismatch`** for everyone: `OAUTH_CALLBACK_URL` doesn't match an authorized redirect URI,
  often because it starts with `http://`. See [redirect_uri_mismatch](google-sign-in.md#redirect_uri_mismatch).
- **A teacher gets a student's page**: their address isn't in `TEACHER_EMAILS`, or the app is in the student view
  (`SELECT teachers_see_student_view FROM history.settings;`).
- **Everyone was signed out**: `SESSION_SECRET` changed, or 12 hours passed.

## Problems in the page

| Symptom | Likely cause |
|---|---|
| *Something went wrong loading the page. Please reload it.* | The page couldn't reach the server at start-up. Check `/ping`. |
| *Something went wrong, so nothing changed* on every click | The server is failing requests; check the log. |
| An *Application Update* dialog that comes back after reloading | Something between the browser and the server, such as a proxy or CDN, is serving the old `main.bundle.js` despite the server's headers ([Caching](releases.md#caching)). |
| A student is back on the first slide unexpectedly | A release renamed or moved the slide they were on ([Slide decks](slide-decks.md#slide-identity-path-names)). |
| A teacher's moves show in the class pages | Moves made in the student view. **Clear teachers' trial walks** on `/admin`. |

## Build problems

| Error | Fix |
|---|---|
| *The 'expect' declaration 'getService' has no 'actual' declaration* in `compileKotlinJvm` | KSP lost the RPC service while generating code. KSP is held at 2.3.12 for this ([Releases](releases.md#dependencies)); if it still happens, run `./gradlew clean` and build again. |
| An import of `sun.…` classes in a Kotlin file | IntelliJ sometimes auto-imports JDK-internal classes. Delete the import. |
| `jsBrowserTest` fails with nothing run | The Kotest entry point wasn't generated; run `./gradlew clean` and the tests again. |
