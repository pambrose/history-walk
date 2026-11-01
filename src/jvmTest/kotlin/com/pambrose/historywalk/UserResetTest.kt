package com.pambrose.historywalk

import com.pambrose.historywalk.Installs.installAutoHeadResponse
import com.pambrose.historywalk.Installs.installConditionalHeaders
import com.pambrose.historywalk.Routes.appRoutes
import com.pambrose.historywalk.User.Companion.findCurrentSlideForUser
import com.pambrose.historywalk.auth.SignedNonceManager
import com.pambrose.historywalk.auth.assignUserSession
import com.pambrose.historywalk.auth.configureGoogleAuth
import com.pambrose.historywalk.auth.googleOAuthSettings
import com.pambrose.historywalk.auth.installUserSessions
import com.pambrose.historywalk.dbms.DbmsTxs.decisionCount
import com.pambrose.historywalk.dbms.DbmsTxs.updateLastSlide
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode.Companion.Found
import io.ktor.http.Url
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

/**
 * `/userreset` deletes the caller's choices and sends their walk back to the first slide. It is a POST, as logout is,
 * so a GET (any link, image, or prefetch) or a HEAD (AutoHeadResponse turns one into a GET) deletes nothing, and it
 * needs a session and touches only the caller's own rows.
 */
class UserResetTest : StringSpec() {
  private lateinit var db: TestPostgres

  init {
    beforeSpec { db = TestPostgres() }
    afterSpec { db.close() }
    beforeTest { db.deleteAll() }

    "a POST resets the caller's walk and leaves everyone else's alone" {
      val caller = teacher(TEACHER)
      val other = student("27other")
      addChoice(caller, START, MIDDLE)
      addChoice(caller, MIDDLE, WIN)
      addChoice(other, START, MIDDLE)
      transaction {
        updateLastSlide(caller, WIN)
        updateLastSlide(other, MIDDLE)
      }

      resetTest { client ->
        client.get("/test/sign-in/$caller")
        // An empty url-encoded form, as the reset link's ClientUtils.postTo submits
        client.submitForm("/userreset").run {
          status shouldBe Found
          headers[HttpHeaders.Location] shouldBe "/"
          headers[HttpHeaders.CacheControl] shouldBe "no-store, private"
        }
      }

      decisionCount(caller) shouldBe 0
      findCurrentSlideForUser(caller, testDeck) shouldBeSameInstanceAs testDeck.rootSlide
      decisionCount(other) shouldBe 1
      findCurrentSlideForUser(other, testDeck) shouldBeSameInstanceAs testDeck.findSlideByPathName(MIDDLE)
    }

    "a GET or a HEAD deletes nothing and sends the browser home" {
      val caller = teacher(TEACHER)
      addChoice(caller, START, MIDDLE)
      transaction { updateLastSlide(caller, MIDDLE) }

      resetTest { client ->
        client.get("/test/sign-in/$caller")
        listOf(client.get("/userreset"), client.head("/userreset")).forEach { r ->
          r.status shouldBe Found
          r.headers[HttpHeaders.Location] shouldBe "/"
        }
      }

      decisionCount(caller) shouldBe 1
      findCurrentSlideForUser(caller, testDeck) shouldBeSameInstanceAs testDeck.findSlideByPathName(MIDDLE)
    }

    "a POST without a session is sent to Google sign-in and deletes nothing" {
      val someone = student("27someone")
      addChoice(someone, START, MIDDLE)

      resetTest { client ->
        client.submitForm("/userreset").run {
          status shouldBe Found
          Url(headers[HttpHeaders.Location]!!).host shouldBe "accounts.google.com"
        }
      }

      decisionCount(someone) shouldBe 1
    }
  }

  // The app's routes, with a test route that signs in any user id; redirects are returned, not followed
  private fun resetTest(block: suspend (HttpClient) -> Unit) =
    testApplication {
      val teachers = { setOf("$TEACHER@athenian.org") }
      application {
        installConditionalHeaders()
        installAutoHeadResponse()
        installUserSessions(secure = false, teacherEmails = teachers)
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
          appRoutes(allowSlideAccess = false, teacherEmails = teachers)
          get("/test/sign-in/{id}") {
            call.assignUserSession(UUID.fromString(call.parameters["id"]!!))
            call.respondText("ok")
          }
        }
      }
      block(
        createClient {
          install(HttpCookies)
          followRedirects = false
        },
      )
    }

  companion object {
    const val TEACHER = "mrreset"
  }
}
