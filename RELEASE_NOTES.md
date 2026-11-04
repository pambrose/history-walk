# Release notes

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
