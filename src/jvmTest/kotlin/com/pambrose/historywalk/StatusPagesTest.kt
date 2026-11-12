package com.pambrose.historywalk

import com.pambrose.historywalk.Installs.installStatusPages
import com.pambrose.historywalk.Routes.appRoutes
import com.pambrose.historywalk.auth.SessionUser
import com.pambrose.historywalk.auth.SignedNonceManager
import com.pambrose.historywalk.auth.assignUserSession
import com.pambrose.historywalk.auth.configureGoogleAuth
import com.pambrose.historywalk.auth.googleOAuthSettings
import com.pambrose.historywalk.auth.installUserSessions
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.HttpStatusCode.Companion.ServiceUnavailable
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import java.sql.SQLException
import java.util.UUID

/**
 * A request that fails is answered by what went wrong: a 400 for a bad parameter, a 404 where there is nothing, a 503
 * when the database is not answering, and a 500 for anything else, never the 404 every failure used to get. The session
 * check reads the user on every request that carries a session, so with the database down a signed-in browser's every
 * request, the page shell's included, is a 503. `/health` reports the database; `/ping` does not. HealthCheckTest checks
the probe behind `/health`.
 */
class StatusPagesTest : StringSpec() {
  private val teacher = UUID.randomUUID()
  private val emails = mapOf(teacher to "pambrose@athenian.org")
  private val teachers = setOf("pambrose@athenian.org")

  private class Database {
    @Volatile var down = false
  }

  // A fresh app, database switch, and cookie jar for each test; redirects are returned, not followed
  private fun statusTest(block: suspend (HttpClient, Database) -> Unit) =
    testApplication {
      val database = Database()
      application {
        installUserSessions(
          secure = false,
          findUser = { uuid ->
            if (database.down) throw SQLException("Connection refused")
            emails[uuid]?.let { SessionUser(it) }
          },
          teacherEmails = { teachers },
          endSessions = {},
        )
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
        installStatusPages()
        routing {
          appRoutes(allowSlideAccess = true, emails::get, databaseAnswers = { !database.down }) { teachers }
          get("/test/sign-in/{id}") {
            call.assignUserSession(UUID.fromString(call.parameters["id"]!!))
            call.respondText("ok")
          }
          get("/test/bug") { error("a bug") }
          get("/test/query") { throw IllegalStateException("the query failed", SQLException("Connection reset")) }
          get("/test/bad") { throw BadRequestException("no such parameter") }
        }
      }
      block(
        createClient {
          install(HttpCookies)
          followRedirects = false
        },
        database,
      )
    }

  init {
    "with the database down, every request of a signed-in browser is a 503, not a 404" {
      statusTest { client, database ->
        client.get("/test/sign-in/$teacher")
        database.down = true
        listOf("/ping", "/version", "/summary").forEach { path ->
          client.get(path).run {
            status shouldBe ServiceUnavailable
            bodyAsText() shouldStartWith "503 Service Unavailable: the database is not answering"
          }
        }
      }
    }

    "with the database down, a signed-out browser still gets an answer from /ping" {
      statusTest { client, database ->
        database.down = true
        client.get("/ping").run {
          status shouldBe OK
          bodyAsText() shouldBe "pong"
        }
      }
    }

    "a bug is a 500" {
      statusTest { client, _ ->
        client.get("/test/bug").run {
          status shouldBe InternalServerError
          bodyAsText() shouldStartWith "500 Internal Server Error: something went wrong"
        }
      }
    }

    "a database error among the causes is a 503" {
      statusTest { client, _ ->
        client.get("/test/query").status shouldBe ServiceUnavailable
      }
    }

    "a bad request is a 400" {
      statusTest { client, _ ->
        client.get("/test/bad").run {
          status shouldBe BadRequest
          bodyAsText() shouldBe "400 Bad Request"
        }
      }
    }

    "a path with nothing there is still a 404" {
      statusTest { client, _ ->
        client.get("/no-such-page").run {
          status shouldBe NotFound
          bodyAsText() shouldBe "404 Not Found"
        }
      }
    }

    "a slide id that is not a number is a 400, not a server error" {
      statusTest { client, _ ->
        client.get("/test/sign-in/$teacher")
        client.get("/slide/abc").status shouldBe BadRequest
        // A version that is not a number is the first one, as it was
        client.get("/slide/9/abc").status shouldBe OK
      }
    }

    "/health says ok while the database answers, and is a 503 when it does not, never stored" {
      statusTest { client, database ->
        client.get("/health").run {
          status shouldBe OK
          bodyAsText() shouldBe "ok"
          headers[HttpHeaders.CacheControl] shouldBe "no-store"
        }
        database.down = true
        client.get("/health").run {
          status shouldBe ServiceUnavailable
          bodyAsText() shouldBe "the database is not answering"
        }
      }
    }
  }
}
