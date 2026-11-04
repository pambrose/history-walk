# Database

Each deployment has its own Postgres database. Everything the app keeps is in the `history` schema; Flyway keeps its
record of applied migrations in `public.flyway_schema_history`.

## What's in it

| Object | Holds |
|---|---|
| `history.users` | One row per person who has signed in: id, address (lowercase), name, picture link, role at last sign-in, the slide they're on (`last_path_name`), last sign-in, and the session version that sign-out increments. |
| `history.user_choices` | One row per explained decision: who, from which slide to which (path name and title), the choice's text, whether it led to a dead end, the reason, when, and `counted_as_student` for teachers' moves in the student view. A user can have only one row per *from → to* pair. |
| `history.settings` | One row: whether teachers see the student view, and when it last changed. |
| `history.user_visits`, `history.user_decision_counts`, `history.user_reasons` | Views behind the class pages: students' moves, plus teachers' counted ones. |

Deleting a user deletes their choices with them (`ON DELETE CASCADE`).

## Migrations

The server runs [Flyway](https://flywaydb.org) on every startup, before it accepts requests, so every deploy migrates
its database. The migrations are SQL files in `src/jvmMain/resources/db/migration`:

| Migration | Adds |
|---|---|
| `V001__create_schema.sql` | The `history` schema: users, choices, and the progress views. It begins with `drop schema if exists history cascade`. |
| `V002__session_version.sql` | `users.session_version`, so a sign-out ends a user's sessions everywhere. |
| `V003__teachers_see_student_view.sql` | `history.settings` (the student-view switch) and `user_choices.counted_as_student`, and rebuilds the progress views to count teachers' moves made in the student view. |

The log shows what happened at each start: *Flyway applied N migration(s); schema version … -> …*.

Never edit a migration that has been applied anywhere: Flyway checksums them and refuses to start on a mismatch. A
schema change is a new file, `V004__what_it_does.sql`.

### The startup guard

Because `V001` drops the `history` schema, a database whose `history` tables survived without Flyway's record of them
(a restore that left out `public.flyway_schema_history`, or that table dropped or emptied) would look new to Flyway,
which would run `V001` and **delete every user and choice**. The server prevents this: when `V001` is pending but
`history` holds tables, it stops before Flyway writes anything, with an error beginning *Refusing to migrate*. The
error names the fixes:

- **Restore `public.flyway_schema_history`** from the same backup as the `history` schema, then start again. This is
  the usual fix.
- If the `history` schema is known to be at the latest migration, record that deliberately with Flyway's `baseline`
  command at that version.
- To start everyone over instead, drop the `history` schema by hand; the next start rebuilds it empty.

A new, empty database and an already-migrated one both pass the check, which only reads.

## Backups

A backup must include **both** the `history` schema and `public.flyway_schema_history`.

- `heroku pg:backups:capture` and `heroku pg:copy` take whole databases: fine.
- `pg_dump` of the whole database: fine.
- `pg_dump -n history` alone: **not enough**. Restoring it into an empty database trips the startup guard above.

```bash
pg_dump --format=custom --file=history-walk-jermain.dump "$DATABASE_URL"      # everything
pg_restore --clean --if-exists --dbname="$TARGET_URL" history-walk-jermain.dump
```

Backups hold students' names, addresses, and reasons: store them as student records.

## Local databases

`make db` starts Postgres 18 in Docker (container `history-walk`, data in `~/docker/volumes/history-walk`) and creates
the `jermain` and `moses` databases if missing.

| Command | Does |
|---|---|
| `make db` | Starts the container, creating it and the databases the first time. |
| `make stop-db` | Stops it; the data is kept. |
| `make reset-jermain`, `make reset-moses` | Drops and recreates one database. Its data is lost; the next server start rebuilds the schema. |

Connect with `psql -h localhost -U postgres jermain` (password `docker`), or `docker exec -it history-walk psql -U
postgres jermain`.

## Common queries

```sql
-- Who has signed in, newest first
SELECT email, full_name, role, last_sign_in, last_path_name
FROM history.users ORDER BY last_sign_in DESC;

-- A student's moves, in order
SELECT c.created, c.from_title, c.to_title, c.dead_end, c.reason
FROM history.user_choices c JOIN history.users u ON u.id = c.user_uuid_ref
WHERE u.email = '27msmith@athenian.org' ORDER BY c.created;

-- The student-view switch
SELECT teachers_see_student_view, updated FROM history.settings;
```

### Deleting a user's walk

To start one person's walk over, keeping their account:

```sql
BEGIN;
DELETE FROM history.user_choices
WHERE user_uuid_ref = (SELECT id FROM history.users WHERE email = '27msmith@athenian.org');
UPDATE history.users SET last_path_name = '/' WHERE email = '27msmith@athenian.org';
COMMIT;
```

To remove the person entirely, delete their `history.users` row; their choices go with it. They are recreated, with
an empty walk, if they sign in again.

### Starting a whole class over

To delete every walk at the end of a term but keep the database, delete the choices and send everyone to the first
slide:

```sql
BEGIN;
DELETE FROM history.user_choices;
UPDATE history.users SET last_path_name = '/';
COMMIT;
```

Back up first.
