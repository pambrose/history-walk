# Google Sign-In for History Walk — Design

Date: 2026-10-31
Status: ready for owner review (builder check done; owner answered the teacher-list and shared-device questions)

## Intent

History Walk currently signs people in with a username and password they create themselves through a registration
form. Replace that with Google sign-in, limited to the school's Google Workspace (`athenian.org`), modelled on the
sign-in in `../../canvas-cache` (same stack: KVision 9.7, Kilua RPC 0.0.46, Ktor 3.6).

Two kinds of people use the app:

- **Students** walk the slides. Their choices and reasons are recorded.
- **Teachers** view class progress, and can also walk the slides to test them.

The thing that must not happen: **a student seeing another student's data or progress.**

## Goals

1. Sign in with Google (OAuth 2.0 authorization code flow). Only accounts whose Google hosted domain is
   `athenian.org` and whose email Google has verified get in.
2. No passwords and no registration: the first sign-in creates the person's record; later sign-ins refresh it.
3. Role is decided on the server from the email address (owner's decision, 2026-10-31):
   - An address listed in `TEACHER_EMAILS` is a **teacher**. Only listed teachers get the teacher view; office
     staff, coaches, admissions, and shared or test accounts are not teachers unless listed.
   - Otherwise, an address whose local part starts with two digits (`27msmith@athenian.org`) is a **student**.
   - Any other `athenian.org` address is **refused** at sign-in.
   - The list is checked first, so a listed address is a teacher whatever its form (designer's call). Comparison is
     case-insensitive (designer's call).
4. Show each person's Google photo, name, and email at the top of every slide (see "Screens").
5. Teacher-only pages: class progress (`/summary`, `/reasons`), the slide overview (`/slides`), jumping to a slide
   (`/slide/{id}/{version}`), and reloading slide content (`/contentreset`). Students get a 403.
6. Teachers' own test walks are recorded like anyone's but **left out of the progress pages**.
7. Track users in the database: email, name, photo URL, role, first sign-in, last sign-in.

## Non-goals (left out for now; can be added later)

These exist in canvas-cache but are deliberately not ported (agreed with the owner as the designer's call):

- Rate limiting of sign-in or admin pages
- Content-Security-Policy and other security headers
- Signing a user out on every device at once (session revocation)
- HTTP Basic Auth admin area, WebSocket session checks
- Preserving the page address (`#fragment`) across sign-in (`ReturnAddress`)
- canvas-cache's refusal of student accounts (here students are allowed; the two-digit rule only decides the role)

## Anti-goals (failure even if everything "works")

- A student can read, change, or infer another student's choices, reasons, or progress.
- A student can reach a teacher-only page or act as a teacher.
- An account outside `athenian.org` (personal Gmail, another school) gets in.

## Current state (what is being replaced)

Server (`src/jvmMain/kotlin/com/github/pambrose/`):

- `ConfigureFormAuth.kt` — Ktor `form` auth checking a salted SHA-256 password digest; `skipWhen` a session cookie
  names a valid user.
- `Cookies.kt` — `cookie<UserId>("HistoryWalkAuth")` with **no transform**: the cookie holds the user's UUID in plain
  text, unsigned. `/summary` lists every user's UUID to anyone signed in, so today a student can copy a classmate's
  UUID into their cookie and become them. **This hole must be closed.**
- `Routes.kt` — `post(LOGIN)` sets the cookie; `get(LOGOUT)` clears it; everything else sits in one `authenticate {}`
  block open to any signed-in user, including `/summary`, `/reasons`, `/contentreset`, and the slide routes.
- `User.kt` — `createUser` (salt + digest), `queryUserByEmail`, etc. `ServiceImpl.kt` — `RegisterUserService` and
  `ContentService` (every RPC uses `call.userId`, i.e. only the caller's own record — keep that property).

Client (`src/jsMain/kotlin/com/github/pambrose/`):

- `LoginPanel.kt` — login + registration dialog. `Security.kt` — a `SecurityMgr` whose `LoginService` posts the form
  to `/login`. `Rpc.kt` — `registerUser`, and `withAuth { }` around content RPCs. `MainPanel.kt` — the header strip
  (email, decision counts, logout link, optional reset link).

Shared: `Credentials`, `RegisterData`, `IRegisterUserService` (commonMain).

Database: `history.users` has `salt` and `digest` columns (Flyway `V001__create_schema.sql`). There is no data in any
environment that needs keeping.

## Design

### Sign-in flow (port from canvas-cache)

Reference files in canvas-cache: `auth/ConfigureAuth.kt`, `pages/admin/OAuthCallback.kt`, `auth/OAuthState.kt`,
`auth/SignedNonceManager.kt`, `auth/Pkce.kt`, `auth/Cookies.kt`, `config/ServerConfig.kt` (session config),
`common/ConfigObjects.kt` (`isStudentAddress`), and client `auth/dialogs/OAuthLoginDialog.kt`, `common/Security.kt`.
`docs/AUTHENTICATION.md` there describes the whole thing.

1. `GET /auth/login` redirects to Google with scopes `openid email profile`, `hd=athenian.org`,
   `prompt=select_account`.
2. The OAuth `state` is **signed, not stored** (HMAC keyed from `SESSION_SECRET`, ~10-minute life) and bound to the
   browser by a short-lived `oauth_state` cookie (`SameSite=Lax` so it survives the redirect back). The callback
   clears that cookie whether or not it matches, so a state is good once.
3. **PKCE (S256):** the redirect carries a `code_challenge`; the token request carries the `code_verifier`, derived
   from the signed state so nothing is stored.
4. `GET /auth/callback` exchanges the code, reads the Google profile, and accepts the sign-in only if
   `email_verified` is true **and** the hosted domain (`hd`) is `athenian.org` **and** the address is a listed
   teacher or a student (Goal 3). (The `hd` request parameter is only a hint to Google's account picker; the server
   check is what enforces it.)
5. On success: create the user if new, otherwise update name, photo URL, role, and last sign-in; set the session
   cookie; redirect to `/`.
6. On refusal: redirect to `/?error=<reason>`; the sign-in prompt shows the reason (screen 1b). Log refusals without
   the code or state.

### Sessions

- One cookie, `HistoryWalkAuth`, holding the signed-in user's id (and when the session started), **encrypted and
  signed** (`SessionTransportTransformerEncrypt`, keys derived from `SESSION_SECRET`). `HttpOnly`,
  `SameSite=Strict`, 14-day lifetime. In production also `Secure` and the `__Host-` name prefix.
- The 14-day lifetime is safe because each student has their own device (owner's answer, 2026-10-31); shared
  machines would have needed a browser-session cookie or an idle timeout instead.
- On every authenticated request, the user the cookie names must still exist **and still be allowed** under Goal 3's
  rule (re-evaluated against the current `TEACHER_EMAILS`); otherwise treat the request as signed out. This covers a
  database reset and a teacher removed from the list (designer's call; canvas-cache's `UserSessionValidation` does
  the same for its rule).
- Cookies from the old scheme fail to decrypt and are treated as signed out.
- **Production** means `OAUTH_CALLBACK_URL` is not on a loopback host. In production `SESSION_SECRET` is required
  and startup fails without it; locally a clearly-labelled dev-only fallback secret is allowed (canvas-cache's rule).
- Logout is a `POST /logout` from the header button: it clears the cookie and redirects to `/`.

### Authorization

- **Signed-in guard** for the content RPCs and user routes. An unauthenticated RPC call gets **401, not a redirect**
  (a fetch cannot follow a redirect to Google); the client's `SecurityMgr` then opens the sign-in prompt.
- **Teacher guard** on `/summary`, `/reasons`, `/slides`, `/slide/{slideId}/{version?}`, and `/contentreset`: the
  session's user's email must be in the current `TEACHER_EMAILS` (never anything the browser sends). Students get 403
  with a plain "Teachers only" message.
- `/userreset` (reset your own progress) stays available to anyone signed in; it only touches the caller's record.
- No endpoint accepts a user id from the browser. RPCs keep using the session's user only.
- `getUserInfo` returns name, email, photo URL, and role so the header can render.

### Database (Flyway `V002__google_sign_in.sql`)

- `history.users`: drop `salt` and `digest`; add `picture_url TEXT` (nullable — some accounts have no photo),
  `role TEXT NOT NULL CHECK (role IN ('student','teacher'))`, `last_sign_in TIMESTAMPTZ`. Keep `id UUID` primary key,
  `created`, `updated`, `email` (unique; the lookup key at sign-in), `full_name`, `last_path_name`.
- Recreate the three views (`user_visits`, `user_decision_counts`, `user_reasons`) to include only
  `role = 'student'`, so teachers' test walks never appear in progress pages.
- Never edit `V001`; all changes go in `V002`.

### Screens

Approved mockups (shown in the brainstorming session):

1. **Sign-in prompt** (replaces `LoginPanel`): "History Walk — Sign in with your Athenian Google account" and a
   "Sign in with Google" button that goes to `/auth/login`. After a refused sign-in, the same prompt shows why, e.g.
   "jsmith@gmail.com isn't an athenian.org account. Please sign in with your school account."
2. **Student header** (top of each slide): round photo (initials when there's no photo), **Name** · email; below it
   "Total Decisions" and, when enabled, "Consecutive Correct Decisions"; "logout" on the right.
3. **Teacher header**: same, plus a "teacher" tag after the email, "(your test walk, not counted in class progress)"
   after Total Decisions, and links on the right: "class summary" (`/summary`), "student reasons" (`/reasons`),
   "logout". The optional "reset" link keeps its current behaviour for everyone.

The slide content itself does not change.

### Removed

Registration and passwords end to end: `ConfigureFormAuth.kt`, `LoginPanel.kt`, the client `LoginService`,
`Rpc.registerUser`, `IRegisterUserService` / `RegisterUserService` (and its `initRpc` registration and route),
`RegisterData`, `Credentials`, password hashing in `User.kt`, `post(LOGIN)`. Drop dependencies that become unused
(for example `email-utils` if nothing else needs `isNotValidEmail`).

### Configuration

Environment variables (set on both Heroku apps, Jermain and Moses, and locally):

| Variable | Purpose |
|---|---|
| `GOOGLE_CLIENT_ID` | Google OAuth client id |
| `GOOGLE_CLIENT_SECRET` | Google OAuth client secret |
| `OAUTH_CALLBACK_URL` | e.g. `https://<app>.herokuapp.com/auth/callback`; locally `http://localhost:8080/auth/callback` |
| `SESSION_SECRET` | Key for the session cookie and signed state; required in production |
| `SCHOOL_EMAIL_DOMAIN` | Defaults to `athenian.org` |
| `TEACHER_EMAILS` | Comma-separated teacher addresses; the only accounts with the teacher view. Empty means no teachers |

Google Cloud setup (done by the owner; document it in the README): a "Web application" OAuth client whose
authorized redirect URIs include each Heroku app's `/auth/callback` and `http://localhost:8080/auth/callback`.

### Testing

Follow the owner's conventions: Kotest `StringSpec` with an `init {}` block, MockK where useful.

- Unit: the role rule (listed teacher, two-digit student, unlisted non-digit refused, case-insensitive list match),
  the sign-in decision (verified email + hosted domain, including a personal Gmail and a non-Athenian Workspace),
  signed state (valid, expired, tampered, wrong browser), PKCE derivation, and session cookie round trip / tamper
  rejection.
- Routes (Ktor `testApplication`): unauthenticated RPC → 401; student → 403 on each teacher-only route; teacher → 200;
  a session naming a deleted user, or a teacher no longer listed, is treated as signed out.
- Database: run V001 + V002 on a throwaway Postgres container and check the views exclude teachers.
- Manual: a real Google sign-in on localhost with a student and a teacher account.

## Left to the builder

- File and package layout, and how much canvas-cache code to copy verbatim versus simplify (keep its security
  properties listed above).
- Exact wording of refusal messages beyond the example above; the initials-avatar styling.
- How `getUserInfo`'s richer result is shaped (extend `UserInfo` or add a new type).
