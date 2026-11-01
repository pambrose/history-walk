package com.pambrose.historywalk

import com.pambrose.historywalk.Installs.installAutoHeadResponse
import com.pambrose.historywalk.Installs.installConditionalHeaders
import com.pambrose.historywalk.Routes.appRoutes
import com.pambrose.historywalk.auth.SessionUser
import com.pambrose.historywalk.auth.SignedNonceManager
import com.pambrose.historywalk.auth.assignUserSession
import com.pambrose.historywalk.auth.configureGoogleAuth
import com.pambrose.historywalk.auth.googleOAuthSettings
import com.pambrose.historywalk.auth.installUserSessions
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode.Companion.Forbidden
import io.ktor.http.HttpStatusCode.Companion.Found
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.Url
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import java.util.UUID

/**
 * Sign-in, sign-out, the reset, and the teacher pages belong to one signed-in user, so neither the browser (Back after
 * a sign-out on a shared computer) nor Cloudflare may keep a copy: each goes out `no-store, private`, redirects and
 * refusals included. The server is set up as `Installs.installs` sets it up, with compression, conditional headers,
 * and HEAD answered like GET, so a test fails if one of them drops the header. The summary and reasons pages are
 * stubbed, as they read the database; PagesTest renders them from Postgres.
 */
class NoStoreCachingTest : StringSpec() {
  private val student = UUID.randomUUID()
  private val teacher = UUID.randomUUID()
  private val emails = mapOf(student to "27msmith@athenian.org", teacher to "pambrose@athenian.org")
  private val teachers = setOf("pambrose@athenian.org")
  private val teacherPages = listOf("/summary", "/reasons", "/threaddump", "/slides", "/slide/9")

  // Exactly one Cache-Control, and the no-store one: with two, which one a cache obeys is up to the cache
  private fun HttpResponse.shouldNotBeStored() {
    withClue("Cache-Control of ${call.request.method.value} ${call.request.url.encodedPath}") {
      headers.getAll(HttpHeaders.CacheControl) shouldBe listOf("no-store, private")
    }
  }

  // A fresh app and cookie jar for each test; redirects are returned, not followed
  private fun cachingTest(block: suspend (HttpClient) -> Unit) =
    testApplication {
      application {
        install(Compression)
        installConditionalHeaders()
        installAutoHeadResponse()
        // Every user at session version 0, and signing out ends no other session (SignOutTest checks both)
        installUserSessions(
          secure = false,
          findUser = { emails[it]?.let { email -> SessionUser(email) } },
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
        routing {
          appRoutes(allowSlideAccess = true, emails::get) { teachers }
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

  init {
    beforeSpec {
      mockkObject(Pages)
      every { Pages.displayUserSummary() } returns "<p>the class summary</p>"
      every { Pages.displayUserReasons() } returns "<p>the class's reasons</p>"
    }

    afterSpec { unmockkObject(Pages) }

    "every teacher page a teacher opens is never stored" {
      cachingTest { client ->
        client.get("/test/sign-in/$teacher")
        teacherPages.forEach { path ->
          client.get(path).run {
            withClue(path) { status shouldBe OK }
            shouldNotBeStored()
          }
        }
        client.get("/summary").bodyAsText() shouldBe "<p>the class summary</p>"
      }
    }

    // A browser asks for gzip, which Compression applies; curl -sI sends HEAD, which AutoHeadResponse answers
    "a teacher page keeps the header when compressed, checked with If-None-Match, or asked for with HEAD" {
      cachingTest { client ->
        client.get("/test/sign-in/$teacher")
        client.get("/summary") { header(HttpHeaders.AcceptEncoding, "gzip") }.shouldNotBeStored()
        client.get("/summary") { header(HttpHeaders.IfNoneMatch, "\"an-old-copy\"") }.run {
          status shouldBe OK
          shouldNotBeStored()
        }
        client.head("/reasons").run {
          status shouldBe OK
          shouldNotBeStored()
          bodyAsBytes().size shouldBe 0
        }
      }
    }

    "a student is refused each teacher page with a response that is never stored" {
      cachingTest { client ->
        client.get("/test/sign-in/$student")
        teacherPages.forEach { path ->
          client.get(path).run {
            withClue(path) { status shouldBe Forbidden }
            shouldNotBeStored()
          }
        }
      }
    }

    "a signed-out visitor to a teacher page or the reset is sent to Google sign-in with no-store" {
      cachingTest { client ->
        listOf(client.get("/summary"), client.post("/userreset")).forEach { r ->
          r.status shouldBe Found
          Url(r.headers[HttpHeaders.Location]!!).host shouldBe "accounts.google.com"
          r.shouldNotBeStored()
        }
      }
    }

    "sign-in and sign-out are never stored, redirects included" {
      cachingTest { client ->
        client.get("/auth/login").run {
          status shouldBe Found
          Url(headers[HttpHeaders.Location]!!).host shouldBe "accounts.google.com"
          shouldNotBeStored()
        }
        // A callback this server did not start goes back to the sign-in prompt
        client.get("/auth/callback?code=x&state=bogus").run {
          status shouldBe Found
          shouldNotBeStored()
        }
        client.get("/auth/greeting").run {
          status shouldBe OK
          shouldNotBeStored()
        }

        client.get("/test/sign-in/$teacher")
        // A signed-in browser is sent home from the sign-in route
        client.get("/auth/login").run {
          headers[HttpHeaders.Location] shouldBe "/"
          shouldNotBeStored()
        }
        client.get("/logout").shouldNotBeStored()
        client.post("/logout").run {
          headers[HttpHeaders.Location] shouldBe "/"
          shouldNotBeStored()
        }
      }
    }

    "the reset's GET and HEAD, which reset nothing, are never stored" {
      cachingTest { client ->
        client.get("/test/sign-in/$teacher")
        listOf(client.get("/userreset"), client.head("/userreset")).forEach { r ->
          r.status shouldBe Found
          r.headers[HttpHeaders.Location] shouldBe "/"
          r.shouldNotBeStored()
        }
      }
    }

    // StaticContentCachingTest builds its own routes, so only this test sees what appRoutes does to them
    "the page shell, the bundle, and the images keep their own caching" {
      cachingTest { client ->
        client.get("/test/sign-in/$teacher")
        listOf("/", "/main.bundle.js", "/static/images/mansion.jpg").forEach { path ->
          client.get(path).run {
            withClue(path) {
              status shouldBe OK
              headers.getAll(HttpHeaders.CacheControl) shouldBe listOf("no-cache, private")
              headers[HttpHeaders.ETag].orEmpty() shouldStartWith "\""
            }
          }
        }
      }
    }

    "health and version are left alone" {
      cachingTest { client ->
        listOf("/ping", "/version").forEach { path ->
          client.get(path).run {
            withClue(path) {
              status shouldBe OK
              headers[HttpHeaders.CacheControl] shouldBe null
            }
          }
        }
      }
    }
  }
}
