package com.pambrose.historywalk.auth

import com.pambrose.historywalk.SignInError
import com.pambrose.historywalk.auth.OAuthState.consumeOAuthState
import com.pambrose.historywalk.auth.OAuthState.rememberOAuthState
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.http.HttpMethod
import io.ktor.server.auth.AuthenticationConfig
import io.ktor.server.auth.OAuthServerSettings
import io.ktor.server.auth.oauth
import io.ktor.server.response.respondRedirect
import io.ktor.util.NonceManager

private val logger = KotlinLogging.logger {}

private val CONTROL_CHARACTER = Regex("\\p{Cntrl}")

private const val MAX_LOGGED_CAUSE = 200

/** The Google sign-in provider, named by every `authenticate(GOOGLE_AUTH)` route. */
const val GOOGLE_AUTH = "google-oauth"

/** Where a sign-in that did not complete sends the browser: the sign-in prompt, saying to try again. */
internal val signInFailedUrl: String = SignInDecision.Rejected(SignInError.FAILED, null).redirectUrl()

/**
 * Google's side of sign-in, with PKCE: the authorize request carries an S256 `code_challenge`, and the token request
 * the `code_verifier` it was made from, so a code taken from a callback is no good to anyone without the verifier.
 * The verifier is derived from the signed state ([Pkce.codeVerifier]), which comes back with the callback, so nothing
 * is stored between the two, as the state is not. `hd` asks Google to offer only the school's accounts; it is a hint
 * the browser can drop, so the callback checks the account's hosted domain itself ([signInDecision]).
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
    onStateCreated = { call, state -> call.rememberOAuthState(state) },
    // The state is already among the parameters when the interceptor runs
    authorizeUrlInterceptor = { _ ->
      parameters["state"]?.let { state ->
        parameters.append("code_challenge", Pkce.codeChallenge(Pkce.codeVerifier(state, pkceKey)))
        parameters.append("code_challenge_method", "S256")
      }
    },
    extraTokenParametersProvider = { _, callback ->
      listOf("code_verifier" to Pkce.codeVerifier(callback.state, pkceKey))
    },
  )

/**
 * Google sign-in for the routes under `authenticate(GOOGLE_AUTH)`. A browser without a session is sent to Google; one
 * with a session passes through untouched. A callback that fails -- a stale or forged state, a code Google refuses, an
 * error from Google -- goes back to the sign-in prompt.
 */
fun AuthenticationConfig.configureGoogleAuth(
  callbackUrl: String = OAuthConfig.callbackUrl,
  settings: () -> OAuthServerSettings.OAuth2ServerSettings = { googleOAuthSettings() },
) {
  oauth(GOOGLE_AUTH) {
    urlProvider = { callbackUrl }
    providerLookup = { settings() }
    client = HttpClient(CIO)

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
