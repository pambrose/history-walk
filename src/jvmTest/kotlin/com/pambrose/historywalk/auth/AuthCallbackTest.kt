package com.pambrose.historywalk.auth

import com.pambrose.historywalk.Auth.AUTH_COOKIE
import com.pambrose.historywalk.EndPoints.AUTH_CALLBACK
import com.pambrose.historywalk.EndPoints.AUTH_LOGIN
import com.pambrose.historywalk.Role
import com.pambrose.historywalk.User
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpHeaders.Location
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Found
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.HttpStatusCode.Companion.ServiceUnavailable
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.http.Parameters
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.http.parseUrlEncodedParameters
import io.ktor.http.setCookie
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID

private const val TOKEN_URL = "https://oauth2.googleapis.com/token"
private const val USER_INFO_URL = "https://www.googleapis.com/oauth2/v2/userinfo"
private const val CALLBACK_URL = "http://localhost/auth/callback"
private const val CLIENT_ID = "test-client"
private const val CLIENT_SECRET = "test-secret"
private const val CODE = "4/0AVG7fiQ-the-authorization-code"
private const val ACCESS_TOKEN = "ya29.the-access-token"
private const val STUDENT_NAME = "Maya Smith"
private const val STUDENT_PICTURE = "https://lh3.googleusercontent.com/a/maya"
private const val NOBODY = "nobody"

// The callback admits by the configured school, as the server does
private val schoolDomain = SchoolConfig.emailDomain
private val studentEmail = "27msmith@$schoolDomain"

private val jsonContent = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

// Google's userinfo answer for an account, with fields the server does not read, as Google sends them
private fun userInfoBody(
  email: String = studentEmail,
  hd: String? = schoolDomain,
) = buildJsonObject {
  put("id", "1234567890")
  put("email", email)
  put("verified_email", true)
  put("name", STUDENT_NAME)
  put("given_name", "Maya")
  put("picture", STUDENT_PICTURE)
  hd?.let { put("hd", it) }
}.toString()

/**
 * Google's token and userinfo endpoints, answering with the given statuses and bodies; anything else is a 404.
 * [engine] keeps every request, so a test can read what the server sent.
 */
private class FakeGoogle(
  val tokenStatus: HttpStatusCode = OK,
  val tokenBody: String = """{"access_token":"$ACCESS_TOKEN","expires_in":3599,"token_type":"Bearer"}""",
  val userInfoStatus: HttpStatusCode = OK,
  val userInfoBody: String = userInfoBody(),
) {
  val engine =
    MockEngine { request ->
      when (request.url.toString()) {
        TOKEN_URL -> respond(tokenBody, tokenStatus, jsonContent)
        USER_INFO_URL -> respond(userInfoBody, userInfoStatus, jsonContent)
        else -> respondError(HttpStatusCode.NotFound, "Not Google: ${request.url}")
      }
    }

  val client = HttpClient(engine)

  fun tokenRequests(): List<HttpRequestData> = engine.requestHistory.filter { it.url.toString() == TOKEN_URL }

  fun userInfoRequests(): List<HttpRequestData> = engine.requestHistory.filter { it.url.toString() == USER_INFO_URL }

  /** The form the one token request carried. */
  suspend fun tokenForm(): Parameters = String(tokenRequests().single().body.toByteArray()).parseUrlEncodedParameters()
}

// The OAuth callback end to end: a real /auth/login, then Google's redirect back to /auth/callback, with a MockEngine
// in place of Google's token and userinfo endpoints. The state, and the state cookie, always come from the login's
// response and go back as a browser sends them; nothing here builds either, so the specs hold whatever the cookie
// carries. Provisioning (User.upsertFromGoogle) is mocked, so no database is needed; whether a browser is signed in is
// asked of the app.
class AuthCallbackTest : StringSpec() {
  private val student = UUID.randomUUID()

  private fun Application.module(google: HttpClient) {
    installUserSessions(
      secure = false,
      findEmail = { if (it == student) studentEmail else null },
      teacherEmails = { emptySet() },
    )
    install(Authentication) {
      // The server's settings, but for the client credentials, which it reads from the environment
      configureGoogleAuth(CALLBACK_URL, client = google) {
        googleOAuthSettings(clientId = CLIENT_ID, clientSecret = CLIENT_SECRET)
      }
    }
    routing {
      signInRoutes(userInfoClient = google)
      get("/test/whoami") { call.respondText(call.signedInUserUuid() ?: NOBODY) }
    }
  }

  // A fresh app and Google for each test, with provisioning answering the student's id
  private fun callbackTest(
    google: FakeGoogle = FakeGoogle(),
    block: suspend ApplicationTestBuilder.(FakeGoogle) -> Unit,
  ) {
    mockkObject(User)
    try {
      every { User.upsertFromGoogle(any(), any(), any(), any()) } returns student
      testApplication {
        application { module(google.client) }
        block(google)
      }
    } finally {
      unmockkObject(User)
      google.client.close()
    }
  }

  // A browser: it keeps the cookies it is sent, and redirects are returned, not followed
  private fun ApplicationTestBuilder.browser() =
    createClient {
      install(HttpCookies)
      followRedirects = false
    }

  // Starts a sign-in, as the sign-in dialog's link does, returning where the browser is sent at Google
  private suspend fun HttpClient.startSignIn(): Url {
    val r = get("/$AUTH_LOGIN")
    r.status shouldBe Found
    return Url(r.headers[Location]!!)
  }

  // Google's redirect back after consent: an authorization code, and the state the sign-in went to Google with
  private suspend fun HttpClient.returnFromGoogle(authorizeUrl: Url): HttpResponse =
    get("/$AUTH_CALLBACK") {
      parameter("code", CODE)
      parameter("state", authorizeUrl.parameters["state"])
    }

  private suspend fun HttpClient.signedInAs(): String = get("/test/whoami").bodyAsText()

  // Whether the response signs the browser in: a session cookie with a value
  private fun HttpResponse.setsSession() = setCookie().any { it.name == AUTH_COOKIE && it.value.isNotEmpty() }

  // Whether the response deletes the state cookie: an empty value that expires at once. The tests run outside
  // production, so the cookie has no __Host- prefix.
  private fun HttpResponse.clearsStateCookie() =
    setCookie().any { it.name == OAuthState.stateCookieName(secure = false) && it.value.isEmpty() && it.maxAge == 0 }

  // A refused callback: back to the sign-in prompt, no session for the browser, and no user provisioned
  private suspend fun shouldBeRefused(
    response: HttpResponse,
    browser: HttpClient,
  ) {
    response.status shouldBe Found
    response.headers[Location] shouldBe signInFailedUrl
    response.setsSession() shouldBe false
    browser.signedInAs() shouldBe NOBODY
    verify(exactly = 0) { User.upsertFromGoogle(any(), any(), any(), any()) }
  }

  // RFC 7636's S256: the SHA-256 of the verifier's ASCII bytes, in base64url without padding
  private fun s256(verifier: String): String =
    Base64
      .getUrlEncoder()
      .withoutPadding()
      .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

  init {
    "the callback in the browser that started the sign-in signs the student in and goes home" {
      callbackTest {
        val browser = browser()
        val r = browser.returnFromGoogle(browser.startSignIn())
        r.status shouldBe Found
        r.headers[Location] shouldBe "/"
        r.setsSession() shouldBe true
        // The state is spent
        r.clearsStateCookie() shouldBe true
        browser.signedInAs() shouldBe student.toString()
        verify(exactly = 1) { User.upsertFromGoogle(studentEmail, STUDENT_NAME, STUDENT_PICTURE, Role.STUDENT) }
      }
    }

    "the code is redeemed with the app's credentials, and the account asked for with the token Google gave" {
      callbackTest { google ->
        val browser = browser()
        browser.returnFromGoogle(browser.startSignIn())
        google.tokenRequests().single().method shouldBe HttpMethod.Post
        google.tokenForm().apply {
          this["grant_type"] shouldBe "authorization_code"
          this["code"] shouldBe CODE
          this["client_id"] shouldBe CLIENT_ID
          this["client_secret"] shouldBe CLIENT_SECRET
          this["redirect_uri"] shouldBe CALLBACK_URL
        }
        google.userInfoRequests().single().headers[HttpHeaders.Authorization] shouldBe "Bearer $ACCESS_TOKEN"
      }
    }

    "PKCE: the token request's verifier is the one the challenge sent to Google was made from" {
      callbackTest { google ->
        val browser = browser()
        val authorizeUrl = browser.startSignIn()
        browser.returnFromGoogle(authorizeUrl)
        val verifier = google.tokenForm()["code_verifier"]!!
        // RFC 7636 4.1: 43 to 128 unreserved characters
        verifier shouldMatch Regex("[A-Za-z0-9._~-]{43,128}")
        authorizeUrl.parameters["code_challenge_method"] shouldBe "S256"
        authorizeUrl.parameters["code_challenge"] shouldBe s256(verifier)
        // Only the challenge goes through the browser, never the verifier
        authorizeUrl.toString() shouldNotContain verifier
      }
    }

    "a callback without the state cookie gets no session and provisions no one" {
      callbackTest {
        val authorizeUrl = browser().startSignIn()
        // The callback URL opened in a browser that started no sign-in, as a stolen or planted link would be
        val elsewhere = browser()
        shouldBeRefused(elsewhere.returnFromGoogle(authorizeUrl), elsewhere)
      }
    }

    "a callback with another sign-in's state cookie gets no session and provisions no one" {
      callbackTest {
        val authorizeUrl = browser().startSignIn()
        // The callback URL opened in a browser holding the cookie of a sign-in it started itself
        val other = browser()
        other.startSignIn()
        shouldBeRefused(other.returnFromGoogle(authorizeUrl), other)
      }
    }

    "a code Google refuses goes back to the prompt and clears the state cookie" {
      val google =
        FakeGoogle(
          tokenStatus = BadRequest,
          tokenBody = """{"error":"invalid_grant","error_description":"Bad Request"}""",
        )
      callbackTest(google) {
        val browser = browser()
        val r = browser.returnFromGoogle(browser.startSignIn())
        r.clearsStateCookie() shouldBe true
        shouldBeRefused(r, browser)
        google.userInfoRequests().shouldBeEmpty()
      }
    }

    "a token endpoint that is down goes back to the prompt and clears the state cookie" {
      callbackTest(FakeGoogle(tokenStatus = ServiceUnavailable, tokenBody = "Service Unavailable")) { google ->
        val browser = browser()
        val r = browser.returnFromGoogle(browser.startSignIn())
        r.clearsStateCookie() shouldBe true
        shouldBeRefused(r, browser)
        google.userInfoRequests().shouldBeEmpty()
      }
    }

    "account details Google will not give go back to the prompt with no session" {
      val google =
        FakeGoogle(
          userInfoStatus = Unauthorized,
          userInfoBody = """{"error":{"code":401,"message":"Invalid Credentials","status":"UNAUTHENTICATED"}}""",
        )
      callbackTest(google) {
        val browser = browser()
        shouldBeRefused(browser.returnFromGoogle(browser.startSignIn()), browser)
      }
    }

    "an account outside the school gets no session and is told why" {
      callbackTest(FakeGoogle(userInfoBody = userInfoBody(email = "jsmith@gmail.com", hd = null))) {
        val browser = browser()
        val r = browser.returnFromGoogle(browser.startSignIn())
        r.headers[Location] shouldBe "/?error=domain&email=jsmith%40gmail.com"
        r.setsSession() shouldBe false
        browser.signedInAs() shouldBe NOBODY
        verify(exactly = 0) { User.upsertFromGoogle(any(), any(), any(), any()) }
      }
    }
  }
}
