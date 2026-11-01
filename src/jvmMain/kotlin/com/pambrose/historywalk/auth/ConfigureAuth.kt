package com.pambrose.historywalk.auth

import com.pambrose.historywalk.SignInError
import com.pambrose.historywalk.auth.OAuthState.consumeOAuthState
import com.pambrose.historywalk.auth.OAuthState.oauthStateSecret
import com.pambrose.historywalk.auth.OAuthState.rememberOAuthState
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.http.HttpMethod
import io.ktor.server.auth.AuthenticationConfig
import io.ktor.server.auth.OAuthServerSettings
import io.ktor.server.auth.oauth
import io.ktor.server.response.respondRedirect
import io.ktor.util.AttributeKey
import io.ktor.util.NonceManager

private val logger = KotlinLogging.logger {}

private val CONTROL_CHARACTER = Regex("\\p{Cntrl}")

private const val MAX_LOGGED_CAUSE = 200

/** The Google sign-in provider, named by every `authenticate(GOOGLE_AUTH)` route. */
const val GOOGLE_AUTH = "google-oauth"

/** Where a sign-in that did not complete sends the browser: the sign-in prompt, saying to try again. */
internal val signInFailedUrl: String = SignInDecision.Rejected(SignInError.FAILED, null).redirectUrl()

// The secret onStateCreated puts in the state cookie, kept on the call for authorizeUrlInterceptor, which runs next
private val SIGN_IN_SECRET = AttributeKey<String>("SignInSecret")

/**
 * Google's side of sign-in, with PKCE: the authorize request carries an S256 `code_challenge`, and the token request
 * the `code_verifier` it was made from, so a code taken from a callback is no good to anyone without the verifier.
 * The verifier is derived from a random secret that only the browser that started the sign-in has, in its state
 * cookie ([OAuthState], [Pkce.codeVerifier]). Nothing is stored on the server between the two requests, and the
 * callback URL alone, state and all, does not give the server the verifier. `hd` asks Google to offer only the
 * school's accounts; it is a hint the browser can drop, so the callback checks the account's hosted domain itself
 * ([signInDecision]).
 *
 * The order in which Ktor (3.6.1) calls these hooks is what lets them share the secret. Starting a sign-in, in one
 * call: [nonceManager] makes the state, `onStateCreated` makes the secret and sets the cookie, and
 * `authorizeUrlInterceptor` adds the challenge. At a callback that brings a code: `verifyState`, then
 * `extraTokenParametersProvider`, then the token request. A refusal by either hook, or by Google, ends in the
 * provider's fallback ([configureGoogleAuth]); when `verifyState` refuses, the code is never sent to Google.
 */
fun googleOAuthSettings(
  clientId: String = OAuthConfig.googleClientId,
  clientSecret: String = OAuthConfig.googleClientSecret,
  hostedDomain: String = SchoolConfig.emailDomain,
  pkceKey: ByteArray = SessionConfig.pkceKey,
  nonceManager: NonceManager = SignedNonceManager.instance,
): OAuthServerSettings.OAuth2ServerSettings =
  OAuthServerSettings.OAuth2ServerSettings(
    name = "google",
    authorizeUrl = "https://accounts.google.com/o/oauth2/v2/auth",
    accessTokenUrl = "https://oauth2.googleapis.com/token",
    requestMethod = HttpMethod.Post,
    clientId = clientId,
    clientSecret = clientSecret,
    defaultScopes = listOf("openid", "email", "profile"),
    extraAuthParameters = listOf("hd" to hostedDomain, "prompt" to "select_account"),
    nonceManager = nonceManager,
    onStateCreated = { call, state -> call.attributes.put(SIGN_IN_SECRET, call.rememberOAuthState(state)) },
    authorizeUrlInterceptor = { request ->
      val verifier = Pkce.codeVerifier(request.call.attributes[SIGN_IN_SECRET], pkceKey)
      parameters.append("code_challenge", Pkce.codeChallenge(verifier))
      parameters.append("code_challenge_method", "S256")
    },
    // Ktor's default checks only that this server signed the state; this also checks that the cookie says this browser
    // started the sign-in. Checked here, before the token request, a stolen callback URL's code is never sent to Google
    verifyState = { call, state -> nonceManager.verifyNonce(state.orEmpty()) && call.oauthStateSecret(state) != null },
    extraTokenParametersProvider = { call, callback ->
      // verifyState has just found the secret; without one, the callback never gets here
      val secret = checkNotNull(call.oauthStateSecret(callback.state)) { "The callback has no state cookie" }
      listOf("code_verifier" to Pkce.codeVerifier(secret, pkceKey))
    },
  )

/**
 * Google sign-in for the routes under `authenticate(GOOGLE_AUTH)`. A browser without a session is sent to Google; one
 * with a session passes through untouched. A callback that fails -- a stale or forged state, a state this browser's
 * cookie is not bound to, a code Google refuses, an error from Google -- goes back to the sign-in prompt. [client]
 * redeems the code at Google's token endpoint; a test passes one with a `MockEngine` in place of Google.
 */
fun AuthenticationConfig.configureGoogleAuth(
  callbackUrl: String = OAuthConfig.callbackUrl,
  client: HttpClient = HttpClient(CIO),
  settings: () -> OAuthServerSettings.OAuth2ServerSettings = { googleOAuthSettings() },
) {
  oauth(GOOGLE_AUTH) {
    urlProvider = { callbackUrl }
    providerLookup = { settings() }
    this.client = client

    fallback = { cause ->
      // The fallback answers the callback, so the route's handler, which clears the state cookie, never runs: clear
      // it here too, so a state is good for one callback however that callback ends
      consumeOAuthState()
      // The cause names what failed, never the code or the state; it can quote Google's error text from the query
      // string, so it is cut short and stripped of control characters before it reaches the log
      val reason = cause.message.take(MAX_LOGGED_CAUSE).replace(CONTROL_CHARACTER, "?")
      logger.warn { "Google sign-in did not complete: $reason" }
      respondRedirect(signInFailedUrl)
    }

    skipWhen { call -> call.signedInUserUuid() != null }
  }
}
