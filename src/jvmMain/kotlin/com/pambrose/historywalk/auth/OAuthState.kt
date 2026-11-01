package com.pambrose.historywalk.auth

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.Cookie
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.path
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlin.time.Duration.Companion.minutes

/**
 * Binds the OAuth `state` to the browser that started the sign-in, and keeps that browser's PKCE secret.
 *
 * The state alone only says that this server started *a* sign-in in the last ten minutes: [SignedNonceManager] signs
 * it and stores nothing, and it travels in the URLs to Google and back. Nothing in it says which browser it was issued
 * to, so the sign-in also leaves a cookie in that browser, for two reasons:
 * - Login CSRF. An attacker could start a sign-in with their own account, complete Google consent, keep the callback
 *   URL unopened, and lure a victim into opening it: the state verifies, and the victim's browser is handed the
 *   attacker's session. The callback is honoured only for the browser whose cookie is bound to its state, and an
 *   attacker cannot plant a cookie in someone else's browser (`__Host-` in production).
 * - A stolen callback URL. Whoever obtains a victim's unused callback URL can send it with any cookie they like, the
 *   state included. So the cookie holds a random secret ([newSecret]) that never leaves the victim's browser, which the
 *   PKCE verifier is derived from ([Pkce.codeVerifier]), and a tag, an HMAC of the state and the secret under a server
 *   key, that binds the two. A cookie made up for a stolen state has no valid tag, so the callback is refused before
 *   its code is redeemed, and without the victim's secret nobody can produce the verifier Google expects anyway.
 *
 * Nothing is stored on the server: the cookie is the only copy of the secret. Clearing it at the callback is what
 * makes a state good for one callback.
 */
object OAuthState {
  private val logger = KotlinLogging.logger {}

  const val STATE_COOKIE = "oauth_state"
  private val STATE_TTL = 10.minutes

  // 256 bits, which base64url turns into 43 characters, as many as the verifier made from it
  private const val SECRET_BYTES = 32

  private val secureRandom = SecureRandom()

  /**
   * The cookie a sign-in is remembered by, holding [value] ([stateCookieValue]). It outlives only the trip to Google,
   * is kept from scripts, and is sent on the redirect back from Google, which is a cross-site navigation:
   * `SameSite=Strict` would withhold it and every sign-in would fail. [secure] is false outside production, where the
   * app is served over plain HTTP.
   */
  fun oauthStateCookie(
    value: String,
    secure: Boolean = SessionConfig.isProduction,
  ): Cookie =
    Cookie(
      name = stateCookieName(secure),
      value = value,
      maxAge = STATE_TTL.inWholeSeconds.toInt(),
      path = "/",
      httpOnly = true,
      secure = secure,
      extensions = mapOf("SameSite" to "Lax"),
    )

  /** The state cookie's name: [STATE_COOKIE], with the `__Host-` prefix where it is [secure] (see [hostCookieName]). */
  fun stateCookieName(secure: Boolean): String = hostCookieName(STATE_COOKIE, secure)

  /** A new sign-in's secret: random bytes in base64url, so it has no dot, which the cookie's value splits on. */
  fun newSecret(): String =
    Base64
      .getUrlEncoder()
      .withoutPadding()
      .encodeToString(ByteArray(SECRET_BYTES).also { secureRandom.nextBytes(it) })

  /** The state cookie's value for the sign-in begun with [state] and [secret]: `secret.tag`, the tag binding them. */
  fun stateCookieValue(
    state: String,
    secret: String,
    key: ByteArray,
  ): String = "$secret.${bindingTag(state, secret, key)}"

  /**
   * The secret in [cookie] if the cookie was issued along with [state], else null: for no cookie, a cookie from another
   * sign-in, a cookie holding the bare state (what the cookie held before it had a secret, and what a replay of a
   * stolen callback URL can offer), or a forged one.
   */
  fun secretBoundTo(
    state: String?,
    cookie: String?,
    key: ByteArray,
  ): String? {
    if (state.isNullOrEmpty() || cookie == null) return null
    val parts = cookie.split('.')
    if (parts.size != 2) return null
    val (secret, tag) = parts
    val bound =
      secret.isNotEmpty() && MessageDigest.isEqual(bindingTag(state, secret, key).toByteArray(), tag.toByteArray())
    return secret.takeIf { bound }
  }

  // The secret has no dot (base64url, and the cookie's value splits on dots), so `state.secret` names one pair only
  private fun bindingTag(
    state: String,
    secret: String,
    key: ByteArray,
  ): String = hmacSha256Base64Url(key, "$state.$secret")

  /**
   * Starts this browser's sign-in with [state]: makes the sign-in's secret, remembers it in the state cookie bound to
   * [state], and returns it, for the authorize request's PKCE challenge.
   */
  fun ApplicationCall.rememberOAuthState(
    state: String,
    key: ByteArray = SessionConfig.stateCookieKey,
  ): String =
    newSecret().also { secret -> response.cookies.append(oauthStateCookie(stateCookieValue(state, secret, key))) }

  /** The secret this browser's state cookie holds for [state], or null if the cookie is missing or not bound to it. */
  fun ApplicationCall.oauthStateSecret(
    state: String?,
    key: ByteArray = SessionConfig.stateCookieKey,
    secure: Boolean = SessionConfig.isProduction,
  ): String? = secretBoundTo(state, request.cookies[stateCookieName(secure)], key)

  /**
   * Whether this callback is the sign-in this browser started, clearing the cookie either way: a state is good for
   * one callback, and the cookie is all that keeps it to one.
   */
  fun ApplicationCall.consumeOAuthState(
    key: ByteArray = SessionConfig.stateCookieKey,
    secure: Boolean = SessionConfig.isProduction,
  ): Boolean {
    val matched = oauthStateSecret(request.queryParameters["state"], key, secure) != null
    // Cleared with the attributes it was set with: a browser ignores a __Host- cookie set without Secure, the expired
    // one that deletes it included
    response.cookies.append(oauthStateCookie(value = "", secure = secure).copy(maxAge = 0))

    // A refusal logs the path, not the address: the query carries Google's authorization code and the state
    if (!matched)
      logger.warn { "Refused ${request.path()}: the OAuth state does not match the browser that started sign-in" }
    return matched
  }
}
