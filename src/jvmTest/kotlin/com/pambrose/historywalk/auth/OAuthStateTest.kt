package com.pambrose.historywalk.auth

import com.pambrose.historywalk.auth.OAuthState.newSecret
import com.pambrose.historywalk.auth.OAuthState.oauthStateCookie
import com.pambrose.historywalk.auth.OAuthState.secretBoundTo
import com.pambrose.historywalk.auth.OAuthState.stateCookieValue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldMatch
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Cookie
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode.Companion.Found
import io.ktor.http.Url
import io.ktor.http.setCookie
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.OAuthCallback
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class OAuthStateTest : StringSpec() {
  private val key = ByteArray(32) { 7 }
  private val pkceKey = ByteArray(32) { 3 }

  private val settings =
    googleOAuthSettings(
      clientId = "test-client",
      clientSecret = "test-secret",
      hostedDomain = "athenian.org",
      pkceKey = pkceKey,
      nonceManager = SignedNonceManager(ByteArray(32)),
    )

  // A sign-in as /auth/login begins it: the state and challenge in Google's URL, and the cookie left in the browser
  private data class SignIn(
    val state: String,
    val challenge: String,
    val cookie: Cookie,
  ) {
    // The secret the server put in the cookie, read back the way the callback reads it
    val secret: String? get() = secretBoundTo(state, cookie.value, SessionConfig.stateCookieKey)
  }

  private suspend fun HttpClient.signIn(): SignIn {
    val r = get("/auth/login")
    val loc = Url(r.headers[HttpHeaders.Location]!!)
    return SignIn(
      state = loc.parameters["state"]!!,
      challenge = loc.parameters["code_challenge"]!!,
      cookie = r.setCookie().single { it.name == "oauth_state" },
    )
  }

  // A request to [path] from a browser whose state cookie holds [cookie] (none when null), with [state] in the query
  private suspend fun HttpClient.callback(
    path: String,
    state: String,
    cookie: String?,
    code: String? = null,
  ): HttpResponse =
    get(path) {
      code?.let { parameter("code", it) }
      parameter("state", state)
      cookie?.let { header(HttpHeaders.Cookie, "oauth_state=$it") }
    }

  // What the provider's verifyState says of a callback with [state] from a browser holding [cookie]
  private suspend fun HttpClient.verifyState(
    state: String,
    cookie: String?,
  ): Boolean = callback("/test/verify-state", state, cookie).bodyAsText().toBooleanStrict()

  // The code_verifier the token request would carry for that callback, or null where the provider refuses to make one
  private suspend fun HttpClient.codeVerifier(
    state: String,
    cookie: String?,
  ): String? = callback("/test/code-verifier", state, cookie).bodyAsText().takeIf { it != REFUSED }

  // The provider as the server configures it, plus routes that call its two callback hooks as Ktor calls them, so a
  // test sees what they decide without the token request to Google that follows them
  private fun hooksTest(block: suspend (HttpClient) -> Unit) =
    testApplication {
      application {
        installUserSessions(secure = false, findUser = { null }, teacherEmails = { emptySet() })
        install(Authentication) { configureGoogleAuth("http://localhost/auth/callback") { settings } }
        routing {
          signInRoutes()
          get("/test/verify-state") {
            call.respondText(settings.verifyState(call, call.parameters["state"]).toString())
          }
          get("/test/code-verifier") {
            val callback = OAuthCallback.TokenSingle(token = "code", state = call.parameters["state"]!!)
            val verifier =
              try {
                settings.extraTokenParametersProvider(call, callback).toMap()["code_verifier"]
              } catch (_: IllegalStateException) {
                null
              }
            call.respondText(verifier ?: REFUSED)
          }
        }
      }
      block(createClient { followRedirects = false })
    }

  init {
    "a fresh state verifies" {
      val m = SignedNonceManager(ByteArray(32) { 1 })
      runBlocking { m.verifyNonce(m.newNonce()) } shouldBe true
    }

    "an expired state does not" {
      var now = Instant.fromEpochSeconds(1_000)
      val m = SignedNonceManager(ByteArray(32) { 1 }, clock = { now })
      val s = runBlocking { m.newNonce() }
      now += 11.minutes
      runBlocking { m.verifyNonce(s) } shouldBe false
    }

    "a pushed-back expiry breaks the signature" {
      val now = Instant.fromEpochSeconds(1_000)
      val m = SignedNonceManager(ByteArray(32) { 1 }, clock = { now })
      val parts = runBlocking { m.newNonce() }.split('.').toMutableList()
      parts[1] = (parts[1].toLong() + 3_600).toString()
      runBlocking { m.verifyNonce(parts.joinToString(".")) } shouldBe false
    }

    "another key's state does not verify" {
      val issuer = SignedNonceManager(ByteArray(32) { 1 })
      val other = SignedNonceManager(ByteArray(32) { 2 })
      runBlocking { other.verifyNonce(issuer.newNonce()) } shouldBe false
    }

    "malformed states do not verify" {
      val m = SignedNonceManager(ByteArray(32) { 1 })
      listOf("", "a.b", "a.b.c.d", "a.notanumber.c").forEach { runBlocking { m.verifyNonce(it) } shouldBe false }
    }

    "each sign-in's secret is new, and 43 URL-safe characters with no dot" {
      newSecret() shouldNotBe newSecret()
      newSecret() shouldMatch Regex("[A-Za-z0-9_-]{43}")
    }

    "a cookie gives back the secret of the sign-in it was issued with" {
      val secret = newSecret()
      secretBoundTo("state", stateCookieValue("state", secret, key), key) shouldBe secret
    }

    "a missing cookie gives no secret" {
      secretBoundTo("state", null, key).shouldBeNull()
    }

    "a cookie from another sign-in gives no secret" {
      secretBoundTo("state", stateCookieValue("other-state", newSecret(), key), key).shouldBeNull()
    }

    "a cookie holding the bare state gives no secret" {
      // What the cookie held before it had a secret, and what a replay of a stolen callback URL can offer
      val state = runBlocking { SignedNonceManager(ByteArray(32)).newNonce() }
      secretBoundTo(state, state, key).shouldBeNull()
    }

    "a made-up cookie gives no secret" {
      val secret = newSecret()
      val value = stateCookieValue("state", secret, key)
      // Bound with a key other than the server's, which is all someone without the key can do
      secretBoundTo("state", stateCookieValue("state", secret, ByteArray(32) { 8 }), key).shouldBeNull()
      // The real tag on another secret, a tampered tag, no tag, no secret, an extra part
      secretBoundTo("state", "${newSecret()}.${value.substringAfter('.')}", key).shouldBeNull()
      secretBoundTo("state", value.dropLast(1) + (if (value.last() == 'A') 'B' else 'A'), key).shouldBeNull()
      secretBoundTo("state", secret, key).shouldBeNull()
      secretBoundTo("state", ".${value.substringAfter('.')}", key).shouldBeNull()
      secretBoundTo("state", "$value.x", key).shouldBeNull()
      secretBoundTo("state", "", key).shouldBeNull()
    }

    "a cookie names no secret for a callback without a state" {
      secretBoundTo(null, stateCookieValue("state", newSecret(), key), key).shouldBeNull()
      secretBoundTo("", stateCookieValue("", newSecret(), key), key).shouldBeNull()
    }

    "the state cookie survives the redirect back from Google" {
      oauthStateCookie(stateCookieValue("state", newSecret(), key), secure = true).let {
        it.name shouldBe "__Host-oauth_state"
        it.extensions["SameSite"] shouldBe "Lax"
        it.httpOnly shouldBe true
        it.secure shouldBe true
        it.maxAge shouldBe 600
        it.path shouldBe "/"
      }
    }

    "sign-in leaves a cookie holding a secret bound to the state, never the state itself" {
      hooksTest { client ->
        val signIn = client.signIn()
        signIn.secret.shouldNotBeNull()
        signIn.cookie.value shouldNotContain signIn.state
        signIn.cookie.let {
          it.httpOnly shouldBe true
          it.extensions["SameSite"] shouldBe "Lax"
          it.maxAge shouldBe 600
          it.path shouldBe "/"
        }
      }
    }

    "the challenge sent to Google is made from the cookie's secret, not from the state" {
      hooksTest { client ->
        val signIn = client.signIn()
        signIn.challenge shouldBe Pkce.codeChallenge(Pkce.codeVerifier(signIn.secret!!, pkceKey))
        signIn.challenge shouldNotBe Pkce.codeChallenge(Pkce.codeVerifier(signIn.state, pkceKey))
      }
    }

    "the browser that started the sign-in passes verifyState, and its token request has the challenge's verifier" {
      hooksTest { client ->
        val signIn = client.signIn()
        client.verifyState(signIn.state, signIn.cookie.value) shouldBe true
        Pkce.codeChallenge(client.codeVerifier(signIn.state, signIn.cookie.value)!!) shouldBe signIn.challenge
      }
    }

    "verifyState refuses a callback that brings no state cookie" {
      hooksTest { client ->
        val signIn = client.signIn()
        client.verifyState(signIn.state, null) shouldBe false
        client.codeVerifier(signIn.state, null).shouldBeNull()
      }
    }

    "verifyState refuses a stolen callback URL replayed with its state as the cookie" {
      hooksTest { client ->
        val signIn = client.signIn()
        client.verifyState(signIn.state, signIn.state) shouldBe false
        client.codeVerifier(signIn.state, signIn.state).shouldBeNull()
      }
    }

    "verifyState refuses a stolen callback URL replayed with a cookie from another sign-in" {
      hooksTest { client ->
        val victim = client.signIn()
        val attacker = client.signIn()
        client.verifyState(victim.state, attacker.cookie.value) shouldBe false
        client.codeVerifier(victim.state, attacker.cookie.value).shouldBeNull()
      }
    }

    "verifyState refuses a stolen callback URL replayed with a cookie made up for its state" {
      hooksTest { client ->
        val victim = client.signIn()
        val forged = stateCookieValue(victim.state, newSecret(), ByteArray(32) { 9 })
        client.verifyState(victim.state, forged) shouldBe false
        client.codeVerifier(victim.state, forged).shouldBeNull()
      }
    }

    "a cookie bound to a forged state does not get it past verifyState" {
      hooksTest { client ->
        val state = "forged.1.state"
        client.verifyState(state, stateCookieValue(state, newSecret(), SessionConfig.stateCookieKey)) shouldBe false
      }
    }

    "a sign-in begun before the cookie held a secret fails cleanly at the callback" {
      hooksTest { client ->
        // Its cookie holds the bare state, as a replay's would; verifyState refuses it, so no code goes to Google
        val state = client.signIn().state
        val r = client.callback("/auth/callback", state, cookie = state, code = "x")
        r.status shouldBe Found
        r.headers[HttpHeaders.Location] shouldBe signInFailedUrl
        r.setCookie().any { it.name == "oauth_state" && it.value.isEmpty() && it.maxAge == 0 } shouldBe true
      }
    }
  }

  private companion object {
    const val REFUSED = "refused"
  }
}
