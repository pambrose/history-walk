package com.pambrose.historywalk.auth

import com.pambrose.historywalk.Auth.DEFAULT_LOGIN_GREETING
import com.pambrose.historywalk.EndPoints.AUTH_CALLBACK
import com.pambrose.historywalk.EndPoints.AUTH_GREETING
import com.pambrose.historywalk.EndPoints.AUTH_LOGIN
import com.pambrose.historywalk.EndPoints.LOGOUT
import com.pambrose.historywalk.EnvVar
import com.pambrose.historywalk.User
import com.pambrose.historywalk.auth.OAuthState.consumeOAuthState
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.OAuthAccessTokenResponse
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.sessions
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import java.util.UUID

private val logger = KotlinLogging.logger {}

private const val GOOGLE_USER_INFO_URL = "https://www.googleapis.com/oauth2/v2/userinfo"

private val userInfoJson = Json { ignoreUnknownKeys = true }

/**
 * Google's details of the account [accessToken] was issued for, asked through [client]. Throws if Google does not
 * answer with them. The request asks for success and the answer is decoded here, so any client will do, whatever
 * plugins it has: the server's, or a test's `MockEngine`.
 */
suspend fun fetchGoogleUserInfo(
  accessToken: String,
  client: HttpClient,
): GoogleUserInfo {
  val response =
    client.get(GOOGLE_USER_INFO_URL) {
      bearerAuth(accessToken)
      accept(ContentType.Application.Json)
      expectSuccess = true
    }
  return userInfoJson.decodeFromString<GoogleUserInfo>(response.bodyAsText())
}

/**
 * Finishes the sign-in of the Google account [userInfo] describes. An account [signInDecision] admits is provisioned,
 * signed in, and sent home; a refused one is sent back to the sign-in prompt, told why, and given no session.
 */
suspend fun ApplicationCall.completeSignIn(
  userInfo: GoogleUserInfo,
  provision: (SignInDecision.Admitted) -> UUID = { User.upsertFromGoogle(it.email, it.name, it.picture, it.role) },
  domain: String = SchoolConfig.emailDomain,
  teacherEmails: Set<String> = SchoolConfig.teacherEmails,
) {
  when (val decision = userInfo.signInDecision(domain, teacherEmails)) {
    is SignInDecision.Admitted -> {
      assignUserSession(provision(decision))
      logger.info { "Signed in ${decision.email} as ${decision.role}" }
      respondRedirect("/")
    }

    is SignInDecision.Rejected -> {
      // The reason and the address only: the callback's query carries Google's authorization code and the state
      logger.warn { "Refused sign-in for ${decision.email ?: "an unknown address"}: ${decision.error.code}" }
      respondRedirect(decision.redirectUrl())
    }
  }
}

/** The sign-in dialog's title: [value] (LOGIN_GREETING) trimmed, or [DEFAULT_LOGIN_GREETING] when it is unset or blank. */
internal fun loginGreeting(value: String? = EnvVar.LOGIN_GREETING.getEnvOrNull()): String =
  value?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_LOGIN_GREETING

/**
 * The sign-in and sign-out routes. They are page navigations, so a browser without a session is redirected to Google,
 * which it can follow; they stay outside the RPC guard ([signedInApi]). [userInfoClient] asks Google for the account's
 * details once the code is redeemed ([fetchGoogleUserInfo]); a test passes one with a `MockEngine` in place of Google.
 */
fun Route.signInRoutes(userInfoClient: HttpClient = HttpClient(CIO)) {
  // The sign-in dialog's title. Open to all: the dialog is shown before anyone is signed in.
  get(AUTH_GREETING) { call.respondText(loginGreeting(), ContentType.Text.Plain) }

  authenticate(GOOGLE_AUTH) {
    // The provider sends a browser without a session to Google; one that gets here is signed in already
    get(AUTH_LOGIN) { call.respondRedirect("/") }

    get(AUTH_CALLBACK) {
      // The state says this server started a sign-in; the cookie says this browser did. Both, or the callback is not
      // this browser's to complete.
      if (!call.consumeOAuthState())
        return@get call.respondRedirect(signInFailedUrl)

      val principal =
        call.principal<OAuthAccessTokenResponse.OAuth2>()
          ?: return@get call.respondRedirect(signInFailedUrl)

      val userInfo =
        try {
          fetchGoogleUserInfo(principal.accessToken, userInfoClient)
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          logger.warn(e) { "Google sign-in did not complete: the account's details could not be fetched" }
          return@get call.respondRedirect(signInFailedUrl)
        }

      call.completeSignIn(userInfo)
    }
  }

  post(LOGOUT) {
    call.sessions.clear<UserSession>()
    call.respondRedirect("/")
  }

  // A GET signs nobody out: any page can make a browser send one, with an image or a link. A page loaded before
  // logout became a POST still navigates here, and lands on the app, which posts.
  get(LOGOUT) { call.respondRedirect("/") }
}
