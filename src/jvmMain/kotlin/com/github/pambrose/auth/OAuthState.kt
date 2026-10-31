package com.github.pambrose.auth

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.Cookie
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.path
import kotlin.time.Duration.Companion.minutes

/**
 * Binds the OAuth `state` to the browser that started the sign-in.
 *
 * The state alone only says that this server started *a* sign-in in the last ten minutes: [SignedNonceManager] signs
 * it and stores nothing. Nothing in that says which browser it was issued to, so an attacker could start a sign-in
 * with their own account, complete Google consent, keep the callback URL unopened, and lure a victim into opening it
 * -- the state verifies, and the victim's browser is handed the attacker's session. Writing the state to a cookie as
 * well closes that: the callback is only honoured by the browser holding the matching cookie. Clearing the cookie at
 * the callback is also what makes a state good for one callback.
 */
object OAuthState {
  private val logger = KotlinLogging.logger {}

  const val STATE_COOKIE = "oauth_state"
  private val STATE_TTL = 10.minutes

  /**
   * The cookie a sign-in is remembered by. It outlives only the trip to Google, is kept from scripts, and is sent on
   * the redirect back from Google, which is a cross-site navigation: `SameSite=Strict` would withhold it and every
   * sign-in would fail. [secure] is false outside production, where the app is served over plain HTTP.
   */
  fun oauthStateCookie(
    state: String,
    secure: Boolean = SessionConfig.isProduction,
  ): Cookie =
    Cookie(
      name = stateCookieName(secure),
      value = state,
      maxAge = STATE_TTL.inWholeSeconds.toInt(),
      path = "/",
      httpOnly = true,
      secure = secure,
      extensions = mapOf("SameSite" to "Lax"),
    )

  /** The state cookie's name: [STATE_COOKIE], with the `__Host-` prefix where it is [secure] (see [hostCookieName]). */
  fun stateCookieName(secure: Boolean): String = hostCookieName(STATE_COOKIE, secure)

  /** Whether a callback's [state] names the sign-in this browser started, which its [cookie] holds. */
  fun stateMatchesCookie(
    state: String?,
    cookie: String?,
  ): Boolean = !state.isNullOrEmpty() && state == cookie

  /** Remembers [state] in this browser, so only it can complete the sign-in it started. */
  fun ApplicationCall.rememberOAuthState(state: String) {
    response.cookies.append(oauthStateCookie(state))
  }

  /**
   * Whether this callback is the sign-in this browser started, clearing the cookie either way: a state is good for
   * one callback, and the cookie is all that keeps it to one.
   */
  fun ApplicationCall.consumeOAuthState(secure: Boolean = SessionConfig.isProduction): Boolean {
    val state = request.queryParameters["state"]
    val cookie = request.cookies[stateCookieName(secure)]
    // Cleared with the attributes it was set with: a browser ignores a __Host- cookie set without Secure, the expired
    // one that deletes it included
    response.cookies.append(oauthStateCookie(state = "", secure = secure).copy(maxAge = 0))

    // A refusal logs the path, not the address: the query carries Google's authorization code and the state
    return stateMatchesCookie(state, cookie).also { matched ->
      if (!matched)
        logger.warn { "Refused ${request.path()}: the OAuth state does not match the browser that started sign-in" }
    }
  }
}
