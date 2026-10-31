# Google Sign-In Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace username/password registration and login with Google sign-in limited to `athenian.org`, with
students and listed teachers as the only roles, teacher-only progress pages, and an unforgeable session cookie.

**Architecture:** Port canvas-cache's Google OAuth (signed stateless `state`, PKCE, encrypted+signed session cookie,
per-request session validation) into a new `com.github.pambrose.auth` package; add a Flyway `V002` migration for the
users table and student-only views; replace the client's login/registration dialog with a "Sign in with Google" prompt
and a header showing the Google photo, name, and email.

**Tech Stack:** Kotlin 2.4.20 (JVM 25), Ktor 3.6.1 server + client (CIO), KVision 9.7.0, Kilua RPC 0.0.46,
Exposed 1.6.0, Flyway 13.12.0, Postgres; tests with Kotest 6.2.6, MockK 1.14.11, Ktor test host,
Testcontainers Postgres 2.0.5.

**Spec:** `docs/superpowers/specs/2026-10-31-google-sign-in-design.md`. Reference implementation (read-only):
`/Users/pambrose/git/canvas-cache/canvas-cache` (paths below are relative to its `src/`).

## Global Constraints

- Allowed domain: `athenian.org` (env `SCHOOL_EMAIL_DOMAIN`, default `athenian.org`).
- Role rule, in order: address in `TEACHER_EMAILS` → teacher; local part starts with two digits (`^\d\d`) → student;
  otherwise refused. Case-insensitive; emails are stored lowercased.
- Sign-in accepted only if Google's `verified_email` is true **and** `hd` equals the domain **and** the role rule
  admits the address.
- Env vars (exact names): `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `OAUTH_CALLBACK_URL`, `SESSION_SECRET`,
  `SCHOOL_EMAIL_DOMAIN`, `TEACHER_EMAILS` (comma-separated). The first three are required at startup.
- Production = `OAUTH_CALLBACK_URL` host is not `localhost`, `127.0.0.1`, or `::1`. In production `SESSION_SECRET` is
  required (≥ 16 chars); locally a blank one falls back to a DEV-ONLY secret with a logged warning.
- Session cookie: name `HistoryWalkAuth` (`__Host-HistoryWalkAuth` in production), encrypted + signed, `HttpOnly`,
  `SameSite=Strict`, `Secure` in production, lifetime 14 days.
- OAuth: scopes `openid email profile`; extra params `hd=<domain>`, `prompt=select_account`; PKCE S256; state cookie
  `oauth_state` (`__Host-oauth_state` in production), `SameSite=Lax`, 10 minutes, cleared at the callback.
- Paths: `GET /auth/login`, `GET /auth/callback`, `POST /logout` (a `GET /logout` signs nobody out and redirects to
  `/`). Refusals redirect to `/?error=<code>&email=<address>`.
- Teacher-only: `/summary`, `/reasons`, `/contentreset`, and (when `ALLOW_SLIDE_ACCESS`) `/slides` and
  `/slide/{slideId}/{version?}`. Students get **403** with body `Teachers only`. Unauthenticated RPC calls get **401**.
- No endpoint takes a user id from the browser; RPCs use only the session's user.
- Never edit `V001__create_schema.sql`.
- Tests: Kotest `StringSpec()` with an `init {}` block; MockK where a collaborator is mocked.
- **Commits:** the owner's rule is no commit/push until they say so. Each task ends with a checkpoint that stages its
  files; run `git commit` only if the owner has asked for commits in this session.

## Review Focus

1. **Mixed-case addresses** (`PAmbrose@Athenian.org` from Google or in `TEACHER_EMAILS`): same person, same role, same
   database row. Tests: Task 1 (role), Task 4 (admitted email lowercased), Task 5 (upsert hits the same row).
2. **Messy `TEACHER_EMAILS`** (spaces, blank entries, trailing comma, uppercase): parsed to clean lowercase addresses.
   Test: Task 1.
3. **Google account with no photo** (`picture` absent): admitted, stored as null, header shows initials. Tests:
   Task 4, Task 8.
4. **A leftover cookie from the password era or a garbage cookie**: treated as signed out (prompt shown), never a
   500/404. Test: Task 6.
5. **Callback opened directly or with a stale state** (back button, double click, bookmarked callback URL): redirect
   to `/?error=failed`, not an error page. Test: Task 7.

---

### Task 1: Test setup and the role rule

**Files:**
- Modify: `gradle/libs.versions.toml` (versions `kotest = "6.2.6"`, `mockk = "1.14.11"`, `testcontainers = "2.0.5"`;
  libraries `kotest-runner` = `io.kotest:kotest-runner-junit5`, `kotest-assertions` =
  `io.kotest:kotest-assertions-core`, `mockk` = `io.mockk:mockk`, `testcontainers-postgresql` =
  `org.testcontainers:testcontainers-postgresql`; reuse existing `ktor-server-test-host`)
- Modify: `build.gradle.kts` `jvmTest.dependencies`: replace `kotlin("test")` with the five test libraries above
- Create: `src/commonMain/kotlin/com/github/pambrose/Role.kt`
- Create: `src/jvmMain/kotlin/com/github/pambrose/auth/Roles.kt`
- Test: `src/jvmTest/kotlin/com/github/pambrose/auth/RolesTest.kt`

**Interfaces:**
- Produces: `@Serializable enum class Role { STUDENT, TEACHER }` (commonMain);
  `object Roles { fun parseTeacherEmails(raw: String?): Set<String>; fun roleFor(email: String, teacherEmails: Set<String>): Role? }`
  — `roleFor` does not check the domain (Task 4 does); `teacherEmails` is already lowercased.

- [ ] **Step 1: Write the failing tests** — `class RolesTest : StringSpec()`:

```kotlin
"a listed teacher is a teacher" { roleFor("pambrose@athenian.org", setOf("pambrose@athenian.org")) shouldBe Role.TEACHER }
"the list is checked first, even for a two-digit address" { roleFor("27lab@athenian.org", setOf("27lab@athenian.org")) shouldBe Role.TEACHER }
"a two-digit address is a student" { roleFor("27msmith@athenian.org", emptySet()) shouldBe Role.STUDENT }
"an unlisted non-digit address is refused" { roleFor("coach@athenian.org", setOf("pambrose@athenian.org")) shouldBe null }
"one leading digit is not a student" { roleFor("2msmith@athenian.org", emptySet()) shouldBe null }
"matching ignores case" { roleFor("PAmbrose@Athenian.org", setOf("pambrose@athenian.org")) shouldBe Role.TEACHER }
"messy lists parse cleanly" { parseTeacherEmails(" PAmbrose@Athenian.org, ,jdoe@athenian.org,") shouldBe setOf("pambrose@athenian.org", "jdoe@athenian.org") }
"a missing list is empty" { parseTeacherEmails(null) shouldBe emptySet() }
```

- [ ] **Step 2: Run to verify it fails**
  Run: `./gradlew jvmTest --tests 'com.github.pambrose.auth.RolesTest'`
  Expected: compilation failure (`Unresolved reference 'roleFor'`). If instead "No tests found", the Kotest JUnit
  platform wiring is wrong — fix it before going on.

- [ ] **Step 3: Implement `Role` and `Roles`** as specified in Interfaces.

- [ ] **Step 4: Run to verify it passes** — same command; Expected: 8 tests PASS.

- [ ] **Step 5: Checkpoint** — `git add gradle/libs.versions.toml build.gradle.kts src/commonMain/kotlin/com/github/pambrose/Role.kt src/jvmMain/kotlin/com/github/pambrose/auth/Roles.kt src/jvmTest`

---

### Task 2: Auth configuration and session keys

**Files:**
- Modify: `src/jvmMain/kotlin/com/github/pambrose/EnvVar.kt` — add `GOOGLE_CLIENT_ID`,
  `GOOGLE_CLIENT_SECRET({ getEnvOrNull()?.obfuscate(1) ?: UNASSIGNED })`, `OAUTH_CALLBACK_URL`,
  `SESSION_SECRET({ getEnvOrNull()?.obfuscate(1) ?: UNASSIGNED })`, `SCHOOL_EMAIL_DOMAIN`, `TEACHER_EMAILS`
- Modify: `src/commonMain/kotlin/com/github/pambrose/Constants.kt` — add `object School { const val DEFAULT_DOMAIN = "athenian.org" }`
- Create: `src/jvmMain/kotlin/com/github/pambrose/auth/AuthConfig.kt`
- Test: `src/jvmTest/kotlin/com/github/pambrose/auth/SessionConfigTest.kt`

Port from canvas-cache `jvmMain/.../common/ConfigObjects.kt` (`OAuthConfig`, `SessionConfig`) and
`jvmMain/.../auth/Cookies.kt` (`hostCookieName`).

**Interfaces:**
- Produces:
  - `object SchoolConfig { val emailDomain: String; val teacherEmails: Set<String> }` (lazy; `teacherEmails` =
    `Roles.parseTeacherEmails(TEACHER_EMAILS.getEnvOrNull())`, `emailDomain` lowercased, default `School.DEFAULT_DOMAIN`)
  - `object OAuthConfig { val googleClientId: String; val googleClientSecret: String; val callbackUrl: String }`
    (lazy, `getRequiredEnv()`)
  - `object SessionConfig { const val DEV_ONLY_SECRET: String; const val MIN_SECRET_LENGTH = 16; val isProduction: Boolean; val encryptionKey: ByteArray; val signKey: ByteArray; val oauthStateKey: ByteArray; val pkceKey: ByteArray; fun encryptTransformer(): SessionTransportTransformerEncrypt; internal fun resolveSecret(configured: String, callbackUrl: String): String; internal fun isLocalCallback(callbackUrl: String): Boolean; internal fun deriveKey(secret: String, label: String): ByteArray }`
    — keys use labels `enc`, `mac`, `state`, `pkce`; the transformer uses a 16-byte random IV (canvas-cache comment
    explains why); `isProduction = !isLocalCallback(OAuthConfig.callbackUrl)`.
  - `internal fun hostCookieName(name: String, secure: Boolean): String` (`__Host-` prefix when secure)
  - `fun verifyAuthConfig()` — reads the three required `OAuthConfig` values and `SessionConfig.encryptionKey`, so a
    missing setting fails startup (called from `Main.kt` in Task 7).

- [ ] **Step 1: Write the failing tests** — `class SessionConfigTest : StringSpec()`:

```kotlin
"localhost, 127.0.0.1 and ::1 callbacks are local" {
  listOf("http://localhost:8080/auth/callback", "http://127.0.0.1:3000/auth/callback", "http://[::1]:8080/auth/callback")
    .forEach { isLocalCallback(it) shouldBe true }
}
"a Heroku callback is not local" { isLocalCallback("https://history-walk.herokuapp.com/auth/callback") shouldBe false }
"production requires a secret" { shouldThrow<IllegalArgumentException> { resolveSecret("", "https://x.herokuapp.com/auth/callback") } }
"production rejects a short secret" { shouldThrow<IllegalArgumentException> { resolveSecret("short", "https://x.herokuapp.com/auth/callback") } }
"production uses the trimmed secret" { resolveSecret("  0123456789abcdef0123  ", "https://x.herokuapp.com/auth/callback") shouldBe "0123456789abcdef0123" }
"local falls back to the dev secret" { resolveSecret("", "http://localhost:8080/auth/callback") shouldBe DEV_ONLY_SECRET }
"derived keys are 32 bytes and differ by label" {
  deriveKey("s", "enc").size shouldBe 32
  deriveKey("s", "enc").contentEquals(deriveKey("s", "mac")) shouldBe false
}
"secure cookie names get the __Host- prefix" {
  hostCookieName("HistoryWalkAuth", true) shouldBe "__Host-HistoryWalkAuth"
  hostCookieName("HistoryWalkAuth", false) shouldBe "HistoryWalkAuth"
}
```

- [ ] **Step 2: Run to verify it fails** — `./gradlew jvmTest --tests 'com.github.pambrose.auth.SessionConfigTest'`;
  Expected: compilation failure.
- [ ] **Step 3: Implement the env vars, `School.DEFAULT_DOMAIN`, and `AuthConfig.kt`** per Interfaces.
- [ ] **Step 4: Run to verify it passes** — Expected: 8 tests PASS.
- [ ] **Step 5: Checkpoint** — stage the four files above.

---

### Task 3: Signed OAuth state, PKCE, and the state cookie

**Files:**
- Create: `src/jvmMain/kotlin/com/github/pambrose/auth/SignedNonceManager.kt` (port `jvmMain/.../auth/SignedNonceManager.kt` verbatim, `instance` keyed from `SessionConfig.oauthStateKey`)
- Create: `src/jvmMain/kotlin/com/github/pambrose/auth/Pkce.kt` (port verbatim)
- Create: `src/jvmMain/kotlin/com/github/pambrose/auth/OAuthState.kt` (port; `secure` defaults to `SessionConfig.isProduction`)
- Test: `src/jvmTest/kotlin/com/github/pambrose/auth/OAuthStateTest.kt`

**Interfaces:**
- Consumes: `SessionConfig.oauthStateKey`, `SessionConfig.isProduction`, `hostCookieName` (Task 2)
- Produces: `class SignedNonceManager(key: ByteArray, ttl: Duration = 10.minutes, clock: () -> Instant = { Clock.System.now() }) : NonceManager` with `companion val instance`;
  `internal fun hmacSha256Base64Url(key: ByteArray, text: String): String`;
  `object Pkce { fun codeVerifier(state: String, key: ByteArray): String; fun codeChallenge(verifier: String): String }`;
  `object OAuthState { const val STATE_COOKIE = "oauth_state"; fun oauthStateCookie(state: String, secure: Boolean = SessionConfig.isProduction): Cookie; fun stateCookieName(secure: Boolean): String; fun stateMatchesCookie(state: String?, cookie: String?): Boolean; fun ApplicationCall.rememberOAuthState(state: String); fun ApplicationCall.consumeOAuthState(secure: Boolean = SessionConfig.isProduction): Boolean }`

- [ ] **Step 1: Write the failing tests** — `class OAuthStateTest : StringSpec()`:

```kotlin
"a fresh state verifies" { val m = SignedNonceManager(ByteArray(32) { 1 }); runBlocking { m.verifyNonce(m.newNonce()) } shouldBe true }
"an expired state does not" { var now = Instant.fromEpochSeconds(1_000); val m = SignedNonceManager(ByteArray(32) { 1 }, clock = { now })
  val s = runBlocking { m.newNonce() }; now += 11.minutes; runBlocking { m.verifyNonce(s) } shouldBe false }
"a pushed-back expiry breaks the signature" { /* replace the middle field with a later epoch second */ verifyNonce(tampered) shouldBe false }
"another key's state does not verify" { verifyNonce on SignedNonceManager(ByteArray(32) { 2 }) of a state from key 1 shouldBe false }
"malformed states do not verify" { listOf("", "a.b", "a.b.c.d", "a.notanumber.c").forEach { verifyNonce(it) shouldBe false } }
"PKCE matches RFC 7636 appendix B" { Pkce.codeChallenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk") shouldBe "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM" }
"a verifier is 43 URL-safe characters" { Pkce.codeVerifier("state", ByteArray(32)).let { it.length shouldBe 43; it shouldMatch Regex("[A-Za-z0-9_-]+") } }
"a state matches only its own cookie" {
  stateMatchesCookie("abc", "abc") shouldBe true
  stateMatchesCookie("abc", "abd") shouldBe false
  stateMatchesCookie("abc", null) shouldBe false
  stateMatchesCookie("", "") shouldBe false
}
"the state cookie survives the redirect back from Google" {
  oauthStateCookie("s", secure = true).let { it.name shouldBe "__Host-oauth_state"; it.extensions["SameSite"] shouldBe "Lax"; it.httpOnly shouldBe true; it.secure shouldBe true; it.maxAge shouldBe 600 }
}
```

- [ ] **Step 2: Run to verify it fails** — `./gradlew jvmTest --tests 'com.github.pambrose.auth.OAuthStateTest'`; Expected: compilation failure.
- [ ] **Step 3: Port the three files.**
- [ ] **Step 4: Run to verify it passes** — Expected: 9 tests PASS.
- [ ] **Step 5: Checkpoint** — stage the three source files and the test.

---

### Task 4: The sign-in decision and refusal reasons

**Files:**
- Create: `src/commonMain/kotlin/com/github/pambrose/SignInError.kt`
- Create: `src/jvmMain/kotlin/com/github/pambrose/auth/SignInDecision.kt` (adapt `jvmMain/.../pages/admin/OAuthCallback.kt` `GoogleUserInfo`, `SignInDecision`, `signInDecision`)
- Test: `src/jvmTest/kotlin/com/github/pambrose/auth/SignInDecisionTest.kt`

**Interfaces:**
- Consumes: `Roles.roleFor`, `Role` (Task 1); `School.DEFAULT_DOMAIN` (Task 2)
- Produces:
  - commonMain `enum class SignInError(val code: String) { FAILED("failed"), UNVERIFIED("unverified"), WRONG_DOMAIN("domain"), NOT_ALLOWED("not-allowed"); fun message(email: String?): String; companion object { const val ERROR_PARAM = "error"; const val EMAIL_PARAM = "email"; fun fromCode(code: String?): SignInError? } }`
    — `WRONG_DOMAIN.message("jsmith@gmail.com")` is exactly
    `"jsmith@gmail.com isn't an athenian.org account. Please sign in with your school account."` (domain from
    `School.DEFAULT_DOMAIN`); other wording is the builder's, and every message must make sense with `email == null`.
  - jvm `@Serializable data class GoogleUserInfo(val id: String, val email: String, val name: String, val picture: String? = null, @SerialName("verified_email") val verifiedEmail: Boolean? = null, val hd: String? = null)`
  - `sealed interface SignInDecision { data class Admitted(val email: String, val name: String, val picture: String?, val role: Role) : SignInDecision; data class Rejected(val error: SignInError, val email: String?) : SignInDecision }`
  - `fun GoogleUserInfo.signInDecision(domain: String, teacherEmails: Set<String>): SignInDecision` — checks, in
    order: verified → address ends with `@domain` and `hd` equals `domain` (case-insensitive) → `roleFor` non-null.
    `Admitted.email` is lowercased.
  - `fun SignInDecision.Rejected.redirectUrl(): String` — `/?error=<code>` plus `&email=<URL-encoded>` when known.

- [ ] **Step 1: Write the failing tests** — `class SignInDecisionTest : StringSpec()`, with a helper
  `info(email, hd = "athenian.org", verified = true, picture = "https://lh3.googleusercontent.com/a/x")` (id `"1"`,
  name `"N"`) and
  `teachers = setOf("pambrose@athenian.org")`:

```kotlin
"a student is admitted as a student" { info("27msmith@athenian.org").signInDecision("athenian.org", teachers) shouldBe Admitted("27msmith@athenian.org", "N", "https://lh3.googleusercontent.com/a/x", Role.STUDENT) }
"a listed teacher is admitted as a teacher" { (info("pambrose@athenian.org").signInDecision("athenian.org", teachers) as Admitted).role shouldBe Role.TEACHER }
"an unlisted staff account is refused" { info("coach@athenian.org").signInDecision("athenian.org", teachers) shouldBe Rejected(NOT_ALLOWED, "coach@athenian.org") }
"a personal Gmail is refused" { info("jsmith@gmail.com", hd = null).signInDecision("athenian.org", teachers) shouldBe Rejected(WRONG_DOMAIN, "jsmith@gmail.com") }
"another school's Workspace is refused" { info("27x@other.org", hd = "other.org").signInDecision("athenian.org", teachers) shouldBe Rejected(WRONG_DOMAIN, "27x@other.org") }
"an athenian.org address without hd is refused" { info("27msmith@athenian.org", hd = null).signInDecision("athenian.org", teachers) shouldBe Rejected(WRONG_DOMAIN, "27msmith@athenian.org") }
"an unverified email is refused first" { info("jsmith@gmail.com", hd = null, verified = false).signInDecision("athenian.org", teachers) shouldBe Rejected(UNVERIFIED, "jsmith@gmail.com") }
"a mixed-case address is admitted lowercased" { (info("PAmbrose@Athenian.org", hd = "Athenian.org").signInDecision("athenian.org", teachers) as Admitted).email shouldBe "pambrose@athenian.org" }
"an account with no photo is admitted" { (info("27msmith@athenian.org", picture = null).signInDecision("athenian.org", teachers) as Admitted).picture shouldBe null }
"the refusal redirect encodes the email" { Rejected(WRONG_DOMAIN, "a+b@gmail.com").redirectUrl() shouldBe "/?error=domain&email=a%2Bb%40gmail.com" }
"the wrong-domain message names the account" { SignInError.WRONG_DOMAIN.message("jsmith@gmail.com") shouldBe "jsmith@gmail.com isn't an athenian.org account. Please sign in with your school account." }
"codes round-trip" { SignInError.entries.forEach { SignInError.fromCode(it.code) shouldBe it }; SignInError.fromCode("nope") shouldBe null }
```

- [ ] **Step 2: Run to verify it fails** — `./gradlew jvmTest --tests 'com.github.pambrose.auth.SignInDecisionTest'`; Expected: compilation failure.
- [ ] **Step 3: Implement `SignInError` and `SignInDecision.kt`** per Interfaces.
- [ ] **Step 4: Run to verify it passes** — Expected: 12 tests PASS.
- [ ] **Step 5: Checkpoint** — stage the two source files and the test.

---

### Task 5: Users table, user records, and removing password sign-in on the server

After this task the server has no way to sign in until Task 7; that is expected.

**Files:**
- Create: `src/jvmMain/resources/db/migration/V002__google_sign_in.sql`
- Modify: `src/jvmMain/kotlin/com/github/pambrose/DbmsTables.kt` (`UsersTable`)
- Modify: `src/jvmMain/kotlin/com/github/pambrose/User.kt`
- Modify: `src/jvmMain/kotlin/com/github/pambrose/Dbms.kt` — `migrate(dataSource)` becomes `internal`
- Modify: `src/commonMain/kotlin/com/github/pambrose/Dao.kt` — `UserInfo` gains `pictureUrl: String? = null` and `role: Role = Role.STUDENT`
- Delete: `src/jvmMain/kotlin/com/github/pambrose/ConfigureFormAuth.kt`
- Modify: `ServiceImpl.kt` (delete `RegisterUserService`), `Installs.kt` (drop `configureFormAuth()`, leaving
  `install(Authentication) {}` for Task 7), `Routes.kt` (drop `post(LOGIN)` and `applyRoutes(RegisterUserServiceManager)`),
  `Main.kt` (drop the `IRegisterUserService` registration)
- Modify: `gradle/libs.versions.toml`, `build.gradle.kts` — remove `common-utils-email` (its only use was `Email.isNotValidEmail`)
- Test: `src/jvmTest/kotlin/com/github/pambrose/UserRepositoryTest.kt`

**Interfaces:**
- Consumes: `Role` (Task 1)
- Produces (in `User.Companion`):
  - `fun upsertFromGoogle(email: String, fullName: String, pictureUrl: String?, role: Role): UUID` — looks up by
    lowercased email; updates `full_name`, `picture_url`, `role`, `last_sign_in`, `updated`; or inserts a new row with a
    random UUID and `last_path_name = ROOT`. Returns the row id.
  - `fun emailForUuid(uuid: UUID): String?`
  - `fun userInfoFor(uuid: UUID): UserInfo?`
  - Role is stored as `role.name.lowercase()` (`student` / `teacher`).
- Removed: `salt`, `digest`, `createUser`, `Password`, `isRegisteredEmail`, `isNotRegisteredEmail`,
  `Email.isNotValidEmail`, the `Parameters.get*` helpers.

**Migration content (decided):** drop `salt`, `digest`; add `picture_url TEXT`,
`role TEXT NOT NULL DEFAULT 'student' CHECK (role IN ('student','teacher'))`, `last_sign_in TIMESTAMPTZ`;
`CREATE OR REPLACE VIEW` each of `history.user_visits`, `history.user_decision_counts`, `history.user_reasons` with
its V001 body plus `users.role = 'student'` in the `WHERE` (keep each view's column list identical).

- [ ] **Step 1: Write the failing tests** — `class UserRepositoryTest : StringSpec()`. In `beforeSpec`, start
  `org.testcontainers.postgresql.PostgreSQLContainer("postgres:18")`, build a `HikariDataSource` on it (driver
  `org.postgresql.Driver`), call `Dbms.migrate(ds)`, then `Database.connect(ds)`; stop the container in `afterSpec`.

```kotlin
"both migrations are applied" { /* select version, success from public.flyway_schema_history */ versions shouldBe listOf("001", "002"); all success }
"first sign-in creates the user" { val id = upsertFromGoogle("27msmith@athenian.org", "Maya Smith", null, Role.STUDENT)
  userInfoFor(id) shouldBe UserInfo("Maya Smith", "27msmith@athenian.org", null, Role.STUDENT) }
"a later sign-in updates the same row, whatever the case" { val a = upsertFromGoogle("pambrose@athenian.org", "Paul", null, Role.TEACHER)
  val b = upsertFromGoogle("PAmbrose@Athenian.org", "Paul Ambrose", "https://lh3.googleusercontent.com/a/p", Role.TEACHER)
  b shouldBe a; userInfoFor(a)!!.pictureUrl shouldBe "https://lh3.googleusercontent.com/a/p"; /* last_sign_in is not null */ }
"emailForUuid finds the address, or null" { emailForUuid(id) shouldBe "27msmith@athenian.org"; emailForUuid(UUID.randomUUID()) shouldBe null }
"progress views leave teachers out" { /* insert one user_choices row each for a student and a teacher */
  /* select from history.user_decision_counts, history.user_reasons, history.user_visits */ each contains only the student's id }
```

- [ ] **Step 2: Run to verify it fails** — `./gradlew jvmTest --tests 'com.github.pambrose.UserRepositoryTest'`;
  Expected: compilation failure (Docker must be running for Step 4).
- [ ] **Step 3: Write `V002`, update `UsersTable` (`pictureUrl` = `text("picture_url").nullable()`, `role` =
  `text("role")`, `lastSignIn` = `datetime("last_sign_in").nullable()`), rewrite `User.kt` per Interfaces, and make
  the deletions listed under Files.**
- [ ] **Step 4: Run to verify it passes** — Expected: 5 tests PASS. Also `./gradlew compileKotlinJvm compileKotlinJs`
  succeeds (the client still compiles: `UserInfo`'s new fields have defaults and `RegisterData`/`IRegisterUserService`
  remain in commonMain until Task 8).
- [ ] **Step 5: Checkpoint** — stage everything listed under Files.

---

### Task 6: Encrypted session cookie and per-request validation

**Files:**
- Create: `src/jvmMain/kotlin/com/github/pambrose/auth/UserSession.kt`
- Create: `src/jvmMain/kotlin/com/github/pambrose/auth/SessionValidation.kt` (adapt `jvmMain/.../auth/UserSessionValidation.kt`)
- Delete: `src/jvmMain/kotlin/com/github/pambrose/Cookies.kt`
- Modify: `Installs.kt` — `install(Sessions) { assignCookies() }` → `installUserSessions()`
- Modify: `ServiceImpl.kt`, `Routes.kt` — every `call.userId.uuid` → `call.userUuid`; the existing `get(LOGOUT)`
  clears `UserSession` instead of `UserId` (Task 7 replaces it)
- Modify: `src/commonMain/kotlin/com/github/pambrose/Dao.kt` — delete `UserId`
- Test: `src/jvmTest/kotlin/com/github/pambrose/auth/UserSessionTest.kt`

**Interfaces:**
- Consumes: `SessionConfig.encryptTransformer()`, `SessionConfig.isProduction`, `hostCookieName`, `SchoolConfig.teacherEmails` (Task 2); `Roles.roleFor` (Task 1); `User.emailForUuid` (Task 5); `Auth.AUTH_COOKIE` (existing, `"HistoryWalkAuth"`)
- Produces:
  - `@Serializable data class UserSession(val uuid: String, val created: Long) { fun isExpiredAt(now: Instant): Boolean; companion object { val LIFETIME: Duration = 14.days } }`
  - `fun Application.installUserSessions(secure: Boolean = SessionConfig.isProduction, findEmail: (UUID) -> String? = { User.emailForUuid(it) }, teacherEmails: () -> Set<String> = { SchoolConfig.teacherEmails })`
    — installs `Sessions` with `cookie<UserSession>(hostCookieName(AUTH_COOKIE, secure))` (path `/`, `HttpOnly`,
    `SameSite=strict`, `Secure = secure`, max age `LIFETIME`, `transform(SessionConfig.encryptTransformer())`), then the
    validation plugin.
  - `fun ApplicationCall.signedInUserUuid(): String?` (null when no session or not a UUID)
  - `val ApplicationCall.userUuid: String` (errors when absent; only used behind guards)
  - `fun ApplicationCall.assignUserSession(uuid: UUID)` (sets `UserSession(uuid.toString(), now millis)`)
  - `fun untrustedBecause(session: UserSession?, now: Instant, findEmail: (UUID) -> String?, teacherEmails: Set<String>): String?`
    — null while trusted; otherwise a reason: no session, older than `LIFETIME`, not a UUID, user gone, or
    `roleFor(email, teacherEmails) == null` (no longer allowed). The plugin clears the session when non-null.

- [ ] **Step 1: Write the failing tests** — `class UserSessionTest : StringSpec()`. Unit part with
  `emails = mapOf(student to "27msmith@athenian.org", teacher to "pambrose@athenian.org")`:

```kotlin
"a student's fresh session is trusted" { untrustedBecause(session(student), now, emails::get, teachers) shouldBe null }
"a listed teacher's session is trusted" { untrustedBecause(session(teacher), now, emails::get, teachers) shouldBe null }
"a teacher removed from the list is signed out" { untrustedBecause(session(teacher), now, emails::get, emptySet()).shouldNotBeNull() }
"a deleted user is signed out" { untrustedBecause(session(UUID.randomUUID()), now, emails::get, teachers).shouldNotBeNull() }
"a session past 14 days is signed out" { untrustedBecause(session(student, createdAt = now - 14.days - 1.milliseconds), now, emails::get, teachers).shouldNotBeNull() }
"a non-UUID id is signed out" { untrustedBecause(UserSession("not-a-uuid", now.toEpochMilliseconds()), now, emails::get, teachers).shouldNotBeNull() }
```

  Route part with `testApplication { application { installUserSessions(secure = false, findEmail = emails::get, teacherEmails = { teachers }); routing { get("/set/{id}") { call.assignUserSession(UUID.fromString(call.parameters["id"]!!)); call.respondText("ok") }; get("/who") { call.respondText(call.signedInUserUuid() ?: "none") } } } }`
  and a client with `HttpCookies`:

```kotlin
"the cookie round-trips" { client.get("/set/$student"); client.get("/who").bodyAsText() shouldBe student.toString() }
"a tampered cookie is ignored" { /* GET /who with header Cookie: HistoryWalkAuth=<valid value with one character changed> */ body shouldBe "none" }
"a password-era cookie is ignored" { /* GET /who with header Cookie: HistoryWalkAuth=uuid%3D%23s$student */ body shouldBe "none"; status shouldBe OK }
"a deleted user's cookie is cleared" { client.get("/set/${UUID.randomUUID()}"); client.get("/who").bodyAsText() shouldBe "none" }
"production cookies carry the host prefix and flags" { /* app with secure = true; GET /set/$student; Set-Cookie header */
  header shouldStartWith "__Host-HistoryWalkAuth="; header shouldContain "Secure"; header shouldContain "HttpOnly"; header shouldContain "SameSite=strict"; header shouldContain "Max-Age=1209600" }
```

- [ ] **Step 2: Run to verify it fails** — `./gradlew jvmTest --tests 'com.github.pambrose.auth.UserSessionTest'`; Expected: compilation failure.
- [ ] **Step 3: Implement `UserSession.kt` and `SessionValidation.kt`, delete `Cookies.kt` and `UserId`, and switch callers to `call.userUuid`.**
- [ ] **Step 4: Run to verify it passes** — Expected: 11 tests PASS; `./gradlew jvmTest` all green.
- [ ] **Step 5: Checkpoint** — stage everything listed under Files.

---

### Task 7: Google sign-in routes, guards, and wiring

**Files:**
- Modify: `gradle/libs.versions.toml`, `build.gradle.kts` `jvmMain` — add `ktor-client-cio`
  (`io.ktor:ktor-client-cio-jvm`), `ktor-client-content-negotiation` (`io.ktor:ktor-client-content-negotiation-jvm`),
  and the existing `ktor-serialization-kotlinx-json`
- Create: `src/jvmMain/kotlin/com/github/pambrose/auth/ConfigureAuth.kt` (port `jvmMain/.../auth/ConfigureAuth.kt`, user auth only)
- Create: `src/jvmMain/kotlin/com/github/pambrose/auth/AuthRoutes.kt` (adapt `OAuthCallback.oauthCallback` and `ServerRoutes.signInPages`)
- Create: `src/jvmMain/kotlin/com/github/pambrose/auth/RouteGuards.kt` (port `ServerRoutes.SignedInApiGuard`/`guardedApi`; add the teacher guard)
- Modify: `Routes.kt` (restructure), `Installs.kt` (`install(Authentication) { configureGoogleAuth() }`),
  `Main.kt` (call `verifyAuthConfig()` before `installs()`), `ServiceImpl.kt` (`getUserInfo`)
- Modify: `src/commonMain/kotlin/com/github/pambrose/Constants.kt` — `EndPoints` adds `AUTH_LOGIN = "auth/login"`,
  `AUTH_CALLBACK = "auth/callback"`, `SUMMARY = "summary"`, `REASONS = "reasons"` (keep `LOGIN` until Task 8)
- Test: `src/jvmTest/kotlin/com/github/pambrose/auth/AuthRoutesTest.kt`

**Interfaces:**
- Consumes: everything from Tasks 1–6.
- Produces:
  - `const val GOOGLE_AUTH = "google-oauth"`
  - `fun googleOAuthSettings(clientId: String = OAuthConfig.googleClientId, clientSecret: String = OAuthConfig.googleClientSecret, hostedDomain: String = SchoolConfig.emailDomain, pkceKey: ByteArray = SessionConfig.pkceKey, nonceManager: NonceManager = SignedNonceManager.instance): OAuthServerSettings.OAuth2ServerSettings`
  - `fun AuthenticationConfig.configureGoogleAuth(callbackUrl: String = OAuthConfig.callbackUrl, settings: () -> OAuthServerSettings.OAuth2ServerSettings = { googleOAuthSettings() })`
    — `skipWhen { it.signedInUserUuid() != null }`; `fallback` redirects to `SignInDecision.Rejected(FAILED, null).redirectUrl()`.
  - `suspend fun fetchGoogleUserInfo(accessToken: String): GoogleUserInfo` — `GET https://www.googleapis.com/oauth2/v2/userinfo` with a bearer token, JSON ignoring unknown keys.
  - `suspend fun ApplicationCall.completeSignIn(userInfo: GoogleUserInfo, provision: (SignInDecision.Admitted) -> UUID = { User.upsertFromGoogle(it.email, it.name, it.picture, it.role) }, domain: String = SchoolConfig.emailDomain, teacherEmails: Set<String> = SchoolConfig.teacherEmails)`
    — Admitted: provision, `assignUserSession`, redirect `/`. Rejected: log the reason (never the code or state),
    redirect to `redirectUrl()`, set no session.
  - `fun Route.signInRoutes()` — `authenticate(GOOGLE_AUTH) { get("/auth/login") { redirect "/" }; get("/auth/callback") { if (!call.consumeOAuthState()) redirect FAILED; else principal → fetchGoogleUserInfo → completeSignIn } }`;
    `post("/logout")` clears the session and redirects to `/`; `get("/logout")` redirects to `/` and clears nothing.
  - `fun Route.signedInApi(build: Route.() -> Unit): Route` — 401 when `signedInUserUuid()` is null.
  - `fun Route.teachersOnly(findEmail: (UUID) -> String? = { User.emailForUuid(it) }, teacherEmails: () -> Set<String> = { SchoolConfig.teacherEmails }, build: Route.() -> Unit): Route`
    — 403 `text/plain` body `Teachers only` unless the session user's role is `TEACHER`.
  - `fun Route.appRoutes(allowSlideAccess: Boolean = ALLOW_SLIDE_ACCESS.getEnv(false), findEmail: (UUID) -> String? = { User.emailForUuid(it) }, teacherEmails: () -> Set<String> = { SchoolConfig.teacherEmails })`
    — the whole tree, used by `assignRoutes()`:
    `signInRoutes()`; `signedInApi { authenticate(GOOGLE_AUTH) { applyRoutes(ContentServiceManager) } }`;
    `authenticate(GOOGLE_AUTH) { get(USER_RESET); teachersOnly { get(SUMMARY); get(REASONS); get(CONTENT_RESET); if (allowSlideAccess) { get("$SLIDE/{slideId}/{version?}"); get("slides") } } }`;
    then the existing public `ping`, `version`, `threaddump`, static resources.
  - `ContentService.getUserInfo()` returns `User.userInfoFor(call.userUuid.toUuid())`.

- [ ] **Step 1: Write the failing tests** — `class AuthRoutesTest : StringSpec()`. Test app: `installUserSessions(secure = false, emails::get, { teachers })`;
  `install(Authentication) { configureGoogleAuth("http://localhost/auth/callback") { googleOAuthSettings("test-client", "test-secret", "athenian.org", ByteArray(32), SignedNonceManager(ByteArray(32))) } }`;
  `routing { appRoutes(allowSlideAccess = true, emails::get, { teachers }); get("/test/sign-in/{id}") { call.assignUserSession(UUID.fromString(call.parameters["id"]!!)); call.respondText("ok") }; authenticate(GOOGLE_AUTH) { teachersOnly(emails::get, { teachers }) { get("/probe") { call.respondText("teacher ok") } } } }`;
  `client` with `HttpCookies` and `followRedirects = false`. A mocked `provision: (SignInDecision.Admitted) -> UUID = mockk()`.

```kotlin
"sign-in goes to Google with the school hint and PKCE" { val r = client.get("/auth/login"); r.status shouldBe Found
  val loc = Url(r.headers[Location]!!); loc.host shouldBe "accounts.google.com"
  loc.parameters["hd"] shouldBe "athenian.org"; loc.parameters["prompt"] shouldBe "select_account"; loc.parameters["code_challenge_method"] shouldBe "S256"
  loc.parameters["scope"]!!.split(" ").toSet() shouldBe setOf("openid", "email", "profile"); r.setCookie().map { it.name } shouldContain "oauth_state" }
"a stale or direct callback goes back to the prompt" { client.get("/auth/callback?code=x&state=bogus").headers[Location] shouldBe "/?error=failed" }
"an RPC without a session is 401" { client.post("/rpc/routeContentServiceManager0").status shouldBe Unauthorized }
"students are refused every teacher page" { client.get("/test/sign-in/$student")
  listOf("/summary", "/reasons", "/contentreset", "/slides", "/slide/1").forEach { client.get(it).apply { status shouldBe Forbidden; bodyAsText() shouldBe "Teachers only" } } }
"a listed teacher passes the teacher guard" { client.get("/test/sign-in/$teacher"); client.get("/probe").bodyAsText() shouldBe "teacher ok" }
"a session naming a deleted user is 401 on RPC" { client.get("/test/sign-in/${UUID.randomUUID()}"); client.post("/rpc/routeContentServiceManager0").status shouldBe Unauthorized }
"GET logout keeps the session; POST logout ends it" { client.get("/test/sign-in/$teacher")
  client.get("/logout").headers[Location] shouldBe "/"; client.get("/probe").bodyAsText() shouldBe "teacher ok"
  client.post("/logout").headers[Location] shouldBe "/"; client.post("/rpc/routeContentServiceManager0").status shouldBe Unauthorized }
"an admitted student gets a session and goes home" { every { provision(any()) } returns student
  /* route get("/test/complete") { call.completeSignIn(info("27msmith@athenian.org"), provision, "athenian.org", teachers) } */
  r.headers[Location] shouldBe "/"; r.setCookie().map { it.name } shouldContain "HistoryWalkAuth"; verify(exactly = 1) { provision(match { it.role == Role.STUDENT }) } }
"a refused account gets no session and is told why" { /* completeSignIn(info("jsmith@gmail.com", hd = null), ...) */
  r.headers[Location] shouldBe "/?error=domain&email=jsmith%40gmail.com"; r.setCookie().none { it.name == "HistoryWalkAuth" && it.value.isNotEmpty() } shouldBe true; verify(exactly = 0) { provision(any()) } }
```

  The last two tests call `completeSignIn` through test routes `get("/test/complete-student")` and
  `get("/test/complete-gmail")` added to the same test `routing` block, with the Task 4 `info(...)` helper.

- [ ] **Step 2: Run to verify it fails** — `./gradlew jvmTest --tests 'com.github.pambrose.auth.AuthRoutesTest'`; Expected: compilation failure.
- [ ] **Step 3: Implement the three auth files, restructure `Routes.kt` into `appRoutes`, and wire `Installs.kt`, `Main.kt`, `ServiceImpl.kt`, `Constants.kt`, and the build files.**
- [ ] **Step 4: Run to verify it passes** — Expected: 9 tests PASS; `./gradlew jvmTest` all green; `./gradlew compileKotlinJs` still succeeds.
- [ ] **Step 5: Checkpoint** — stage everything listed under Files.

---

### Task 8: Client sign-in prompt and header

**Files:**
- Create: `src/commonMain/kotlin/com/github/pambrose/UserDisplay.kt`
- Modify: `src/commonMain/kotlin/com/github/pambrose/Dao.kt` (delete `RegisterData`), `Services.kt` (delete `IRegisterUserService`), `Constants.kt` (delete `EndPoints.LOGIN`)
- Delete: `src/commonMain/kotlin/com/github/pambrose/Credentials.kt`, `src/jsMain/kotlin/com/github/pambrose/LoginPanel.kt`
- Create: `src/jsMain/kotlin/com/github/pambrose/SignInDialog.kt` (adapt `jsMain/.../auth/dialogs/OAuthLoginDialog.kt`)
- Modify: `src/jsMain/kotlin/com/github/pambrose/Security.kt` (drop `LoginService`; port `LoginPrompt` from `jsMain/.../common/Security.kt`)
- Modify: `App.kt` (read the refusal from the URL), `Rpc.kt` (drop `registerUser`), `ClientUtils.kt` (add `postTo`), `MainPanel.kt` (header)
- Modify: `webpack.config.d/proxy.js` — proxy `/auth/*`, `/logout`, `/summary`, `/reasons`, `/slides`, `/slide/*`,
  `/contentreset`, `/userreset`, `/rpc/*`, `/rpcsse/*`, and `/rpcws/*` (`ws: true`) to `http://localhost:8080`; drop
  the stale `/login`, `/kv/*`, `/kvsse/*`, `/kvws/*` entries (Kilua RPC uses `/rpc*`)
- Test: `src/jvmTest/kotlin/com/github/pambrose/UserDisplayTest.kt`

**Interfaces:**
- Consumes: `UserInfo(fullName, email, pictureUrl, role)`, `Role` (Tasks 1, 5); `SignInError` (Task 4); `EndPoints.AUTH_LOGIN`, `SUMMARY`, `REASONS` (Task 7)
- Produces:
  - commonMain `fun initialsOf(fullName: String, email: String): String` — up to two uppercase initials from the name's
    first and last words; when the name is blank, the email's first letter uppercased.
  - jsMain `class SignInDialog(signInError: ObservableValue<String?>, navigate: (String) -> Unit = { window.location.href = it }) : Dialog<Boolean>`
    — not closable; shows "History Walk", "Sign in with your Athenian Google account", the error line when set, and a
    "Sign in with Google" button (`fab fa-google`) that navigates to `/auth/login`.
  - `object Security : SecurityMgr()` with `val signInError = ObservableValue<String?>(null)`; `login()` shows the
    dialog once and parks (`LoginPrompt.showOnceAndPark()`).
  - `fun postTo(path: String)` in `ClientUtils` — builds and submits a `<form method="post">` to `path`.
  - Header (per the approved mockups, spec "Screens"): 40px round `<img>` of `pictureUrl` with
    `referrerpolicy="no-referrer"` (Google photo URLs can refuse requests carrying a referrer), or a round initials
    badge when null; "**Name** · email" plus a "teacher" tag for teachers; "Total Decisions: N" plus
    "(your test walk, not counted in class progress)" for teachers; "Consecutive Correct Decisions" unchanged; right
    side: teachers get "class summary" → `/summary` and "student reasons" → `/reasons`; everyone gets "logout" →
    `postTo("/logout")`; the "reset" link is unchanged.
  - `App.main()`: if `URLSearchParams(window.location.search)` has `SignInError.ERROR_PARAM`, set
    `Security.signInError.value = SignInError.fromCode(code)?.message(email)` and clear the query with
    `window.history.replaceState(null, "", "/")`.

- [ ] **Step 1: Write the failing test** — `class UserDisplayTest : StringSpec()`:

```kotlin
"two words give two initials" { initialsOf("Maya Smith", "27msmith@athenian.org") shouldBe "MS" }
"first and last of several words" { initialsOf("Maya  Anne Smith ", "x@athenian.org") shouldBe "MS" }
"one word gives one initial" { initialsOf("Maya", "x@athenian.org") shouldBe "M" }
"a blank name falls back to the email" { initialsOf(" ", "27msmith@athenian.org") shouldBe "2" }
"initials are uppercase" { initialsOf("maya smith", "x@athenian.org") shouldBe "MS" }
```

- [ ] **Step 2: Run to verify it fails** — `./gradlew jvmTest --tests 'com.github.pambrose.UserDisplayTest'`; Expected: compilation failure.
- [ ] **Step 3: Implement `initialsOf`; then the client changes, deletions, and proxy update listed under Files.**
- [ ] **Step 4: Verify** — `./gradlew jvmTest` all green; `make build` succeeds (compiles the JS bundle); and
  `git grep -nE 'RegisterData|IRegisterUserService|registerUser|Credentials\(|LoginPanel|EndPoints.LOGIN\b|UserId\('`
  prints nothing.
- [ ] **Step 5: Checkpoint** — stage everything listed under Files.

---

### Task 9: Setup documentation and end-to-end check

**Files:**
- Modify: `README.md` — new "Google sign-in" section: create a Google Cloud OAuth client ("Web application");
  authorized redirect URIs `https://<jermain-app>.herokuapp.com/auth/callback`,
  `https://<moses-app>.herokuapp.com/auth/callback`, `http://localhost:8080/auth/callback` (running the jar or
  `make server` alone), and `http://localhost:3000/auth/callback` (with `make client`, whose dev server proxies
  `/auth/*`); the env-var table from the spec; `SESSION_SECRET` generated with `openssl rand -hex 32`; locally,
  `OAUTH_CALLBACK_URL` must match the port the browser uses.
- Modify: `heroku.md` — add `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `OAUTH_CALLBACK_URL`, `SESSION_SECRET`,
  `TEACHER_EMAILS` to both apps' config lists.

- [ ] **Step 1: Write the docs** as listed.
- [ ] **Step 2: Full automated check** — `./gradlew clean jvmTest` all green and `make build` succeeds.
- [ ] **Step 3: Manual end-to-end (needs the owner's Google client and accounts — ask for them).** Run the jar on
  Java 25 against a throwaway Postgres 18 container with `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`,
  `OAUTH_CALLBACK_URL=http://localhost:8080/auth/callback`, `TEACHER_EMAILS=<owner's address>`, and the DBMS env vars;
  then confirm in a browser:
  - a student (`NN…@athenian.org`) signs in, sees photo, name, email, and walks a slide; `/summary` answers "Teachers only";
  - the listed teacher sees the "teacher" tag, the test-walk note, and both links; `/summary` lists the student but
    not the teacher;
  - a personal Gmail is refused with the exact wrong-domain message;
  - "logout" returns to the sign-in prompt;
  - `flyway_schema_history` shows `001` and `002`.
- [ ] **Step 4: Checkpoint** — stage `README.md` and `heroku.md`; report results to the owner.
