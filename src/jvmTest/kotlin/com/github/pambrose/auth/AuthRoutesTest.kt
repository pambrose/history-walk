package com.github.pambrose.auth

import com.github.pambrose.Role
import com.github.pambrose.Routes.appRoutes
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders.Location
import io.ktor.http.HttpStatusCode.Companion.Forbidden
import io.ktor.http.HttpStatusCode.Companion.Found
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.http.Url
import io.ktor.http.setCookie
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID

class AuthRoutesTest : StringSpec() {
  private val student = UUID.randomUUID()
  private val teacher = UUID.randomUUID()
  private val emails = mapOf(student to "27msmith@athenian.org", teacher to "pambrose@athenian.org")
  private val teachers = setOf("pambrose@athenian.org")

  private fun info(
    email: String,
    hd: String? = "athenian.org",
    verified: Boolean = true,
    picture: String? = "https://lh3.googleusercontent.com/a/x",
  ) = GoogleUserInfo(id = "1", email = email, name = "N", picture = picture, verifiedEmail = verified, hd = hd)

  private fun Application.module(provision: (SignInDecision.Admitted) -> UUID) {
    installUserSessions(secure = false, findEmail = emails::get, teacherEmails = { teachers })
    install(Authentication) {
      configureGoogleAuth("http://localhost/auth/callback") {
        googleOAuthSettings(
          clientId = "test-client",
          clientSecret = "test-secret",
          hostedDomain = "athenian.org",
          pkceKey = ByteArray(32),
          nonceManager = SignedNonceManager(ByteArray(32)),
        )
      }
    }
    routing {
      appRoutes(allowSlideAccess = true, emails::get, { teachers })
      get("/test/sign-in/{id}") {
        call.assignUserSession(UUID.fromString(call.parameters["id"]!!))
        call.respondText("ok")
      }
      get("/test/complete-student") {
        call.completeSignIn(info("27msmith@athenian.org"), provision, "athenian.org", teachers)
      }
      get("/test/complete-gmail") {
        call.completeSignIn(info("jsmith@gmail.com", hd = null), provision, "athenian.org", teachers)
      }
      signedInApi {
        authenticate(GOOGLE_AUTH) { get("/api-probe") { call.respondText("api ok") } }
      }
      authenticate(GOOGLE_AUTH) {
        teachersOnly(emails::get, { teachers }) {
          get("/probe") { call.respondText("teacher ok") }
        }
      }
    }
  }

  // Whether the response deletes the state cookie: an empty value that expires at once
  private fun HttpResponse.clearsStateCookie() =
    setCookie().any { it.name == "oauth_state" && it.value.isEmpty() && it.maxAge == 0 }

  // A fresh app, cookie jar, and provisioning mock for each test; redirects are returned, not followed
  private fun signInTest(
    provision: (SignInDecision.Admitted) -> UUID = mockk(),
    block: suspend (HttpClient) -> Unit,
  ) = testApplication {
    application { module(provision) }
    block(
      createClient {
        install(HttpCookies)
        followRedirects = false
      },
    )
  }

  init {
    "sign-in goes to Google with the school hint and PKCE" {
      signInTest { client ->
        val r = client.get("/auth/login")
        r.status shouldBe Found
        val loc = Url(r.headers[Location]!!)
        loc.host shouldBe "accounts.google.com"
        loc.parameters["hd"] shouldBe "athenian.org"
        loc.parameters["prompt"] shouldBe "select_account"
        loc.parameters["code_challenge_method"] shouldBe "S256"
        loc.parameters["scope"]!!.split(" ").toSet() shouldBe setOf("openid", "email", "profile")
        r.setCookie().map { it.name } shouldContain "oauth_state"
      }
    }
    "a stale or direct callback goes back to the prompt" {
      signInTest { client ->
        client.get("/auth/callback?code=x&state=bogus").headers[Location] shouldBe "/?error=failed"
      }
    }
    "a callback with a forged state clears the state cookie" {
      signInTest { client ->
        client.get("/auth/login")
        val r = client.get("/auth/callback?code=x&state=bogus")
        r.headers[Location] shouldBe "/?error=failed"
        r.clearsStateCookie() shouldBe true
      }
    }
    "a sign-in cancelled at Google clears the state cookie" {
      signInTest { client ->
        val state = Url(client.get("/auth/login").headers[Location]!!).parameters["state"]!!
        val r = client.get("/auth/callback?error=access_denied&state=$state")
        r.headers[Location] shouldBe "/?error=failed"
        r.clearsStateCookie() shouldBe true
      }
    }
    "a signed-in browser's callback clears the state cookie and signs no one in" {
      signInTest { client ->
        client.get("/auth/login")
        client.get("/test/sign-in/$teacher")
        val r = client.get("/auth/callback?state=x")
        r.headers[Location] shouldBe "/?error=failed"
        r.clearsStateCookie() shouldBe true
        r.setCookie().none { it.name == "HistoryWalkAuth" } shouldBe true
        client.get("/probe").bodyAsText() shouldBe "teacher ok"
      }
    }
    "an RPC without a session is 401" {
      signInTest { client ->
        client.post("/rpc/routeContentServiceManager0").status shouldBe Unauthorized
      }
    }
    "students are refused every teacher page" {
      signInTest { client ->
        client.get("/test/sign-in/$student")
        listOf("/summary", "/reasons", "/contentreset", "/slides", "/slide/1").forEach { path ->
          client.get(path).apply {
            status shouldBe Forbidden
            bodyAsText() shouldBe "Teachers only"
          }
        }
      }
    }
    "a signed-out visitor to a teacher page is sent to Google, not refused" {
      signInTest { client ->
        val r = client.get("/summary")
        r.status shouldBe Found
        Url(r.headers[Location]!!).host shouldBe "accounts.google.com"
      }
    }
    "a listed teacher passes the teacher guard" {
      signInTest { client ->
        client.get("/test/sign-in/$teacher")
        client.get("/probe").bodyAsText() shouldBe "teacher ok"
      }
    }
    "a valid session passes signedInApi, and no session is 401" {
      signInTest { client ->
        client.get("/api-probe").status shouldBe Unauthorized
        client.get("/test/sign-in/$student")
        val r = client.get("/api-probe")
        r.status shouldBe OK
        r.bodyAsText() shouldBe "api ok"
      }
    }
    "a session naming a deleted user is 401 on RPC" {
      signInTest { client ->
        client.get("/test/sign-in/${UUID.randomUUID()}")
        client.post("/rpc/routeContentServiceManager0").status shouldBe Unauthorized
      }
    }
    "GET logout keeps the session; POST logout ends it" {
      signInTest { client ->
        client.get("/test/sign-in/$teacher")
        client.get("/logout").headers[Location] shouldBe "/"
        client.get("/probe").bodyAsText() shouldBe "teacher ok"
        client.post("/logout").headers[Location] shouldBe "/"
        client.post("/rpc/routeContentServiceManager0").status shouldBe Unauthorized
      }
    }
    "an admitted student gets a session and goes home" {
      val provision = mockk<(SignInDecision.Admitted) -> UUID>()
      every { provision(any()) } returns student
      signInTest(provision) { client ->
        val r = client.get("/test/complete-student")
        r.headers[Location] shouldBe "/"
        r.setCookie().map { it.name } shouldContain "HistoryWalkAuth"
        verify(exactly = 1) { provision(match { it.role == Role.STUDENT }) }
      }
    }
    "a refused account gets no session and is told why" {
      val provision = mockk<(SignInDecision.Admitted) -> UUID>()
      signInTest(provision) { client ->
        val r = client.get("/test/complete-gmail")
        r.headers[Location] shouldBe "/?error=domain&email=jsmith%40gmail.com"
        r.setCookie().none { it.name == "HistoryWalkAuth" && it.value.isNotEmpty() } shouldBe true
        verify(exactly = 0) { provision(any()) }
      }
    }
  }
}
