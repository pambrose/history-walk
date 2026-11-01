package com.pambrose.historywalk

import com.pambrose.historywalk.User.Companion.sessionUserFor
import com.pambrose.historywalk.auth.GOOGLE_AUTH
import com.pambrose.historywalk.auth.assignUserSession
import com.pambrose.historywalk.auth.installUserSessions
import com.pambrose.historywalk.auth.signInRoutes
import com.pambrose.historywalk.auth.signedInUserUuid
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders.Cookie
import io.ktor.http.HttpHeaders.Location
import io.ktor.http.HttpHeaders.SetCookie
import io.ktor.http.HttpStatusCode.Companion.Found
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import java.util.UUID

// Signing out against a real database: POST /logout moves the user's session version on, so the cookie it came with,
// any copy of it, and the user's sessions in other browsers no longer sign anyone in
class SignOutTest : StringSpec() {
  private lateinit var db: TestPostgres

  private fun version(user: String) = sessionUserFor(UUID.fromString(user)).shouldNotBeNull().sessionVersion

  init {
    beforeSpec { db = TestPostgres() }
    afterSpec { db.close() }

    "signing out ends the user's sessions in every browser, copies of the cookie included" {
      val user = student("27leaves")
      signOutTest { client ->
        val lab = client.signIn(user)
        val home = client.signIn(user)
        client.who(lab) shouldBe user
        client.who(home) shouldBe user

        client.signOut(lab).apply {
          status shouldBe Found
          headers[Location] shouldBe "/"
          headers[SetCookie].shouldNotBeNull() shouldStartWith "HistoryWalkAuth=;"
        }

        // The lab computer's cookie, sent again as a copy would be, is signed out and cleared
        client.get("/who") { header(Cookie, lab) }.apply {
          bodyAsText() shouldBe "none"
          headers[SetCookie].shouldNotBeNull() shouldStartWith "HistoryWalkAuth=;"
        }
        client.who(home) shouldBe "none"
      }
      version(user) shouldBe 1
    }

    "a sign-in after signing out carries the new version and is trusted" {
      val user = student("27returns")
      signOutTest { client ->
        client.signOut(client.signIn(user))
        val again = client.signIn(user)
        client.who(again) shouldBe user
      }
      version(user) shouldBe 1
    }

    "signing out leaves other users signed in" {
      val leaves = student("27leaves2")
      val stays = student("27stays")
      signOutTest { client ->
        val left = client.signIn(leaves)
        val staying = client.signIn(stays)
        client.signOut(left)
        client.who(staying) shouldBe stays
      }
      version(stays) shouldBe 0
    }

    "only a trusted session moves the version on: not a replayed cookie, no cookie, or a GET" {
      val user = student("27replays")
      signOutTest { client ->
        val old = client.signIn(user)
        client.signOut(old)
        client.signOut(old).headers[Location] shouldBe "/"
        client.post("/logout").headers[Location] shouldBe "/"
        val current = client.signIn(user)
        client.get("/logout") { header(Cookie, current) }.headers[Location] shouldBe "/"
        client.who(current) shouldBe user
      }
      version(user) shouldBe 1
    }
  }

  // The session cookie a sign-in sets, as the browser sends it back
  private suspend fun HttpClient.signIn(user: String): String =
    get("/test/sign-in/$user").headers[SetCookie].shouldNotBeNull().substringBefore(";")

  private suspend fun HttpClient.signOut(cookie: String): HttpResponse = post("/logout") { header(Cookie, cookie) }

  private suspend fun HttpClient.who(cookie: String): String = get("/who") { header(Cookie, cookie) }.bodyAsText()

  // The sign-in routes with the real session store, the users table; the client keeps no cookies, so each request
  // sends only the cookie it is given, and returns redirects rather than following them
  private fun signOutTest(block: suspend (HttpClient) -> Unit) =
    testApplication {
      application {
        installUserSessions(secure = false, teacherEmails = { emptySet() })
        // signInRoutes() names the Google provider; these tests never reach it, so a stand-in that admits no one will do
        install(Authentication) { provider(GOOGLE_AUTH) { authenticate { } } }
        routing {
          signInRoutes()
          get("/test/sign-in/{id}") {
            call.assignUserSession(UUID.fromString(call.parameters["id"]!!))
            call.respondText("ok")
          }
          get("/who") { call.respondText(call.signedInUserUuid() ?: "none") }
        }
      }
      block(createClient { followRedirects = false })
    }
}
