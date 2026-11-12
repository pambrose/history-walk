package com.pambrose.historywalk

import com.pambrose.historywalk.Installs.installSecurityHeaders
import com.pambrose.historywalk.Installs.installStatusPages
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication

/** Every response carries the security headers, a refusal or an error as much as a page; HSTS only in production. */
class SecurityHeadersTest : StringSpec() {
  private fun headersTest(
    production: Boolean,
    block: suspend (HttpClient) -> Unit,
  ) = testApplication {
    application {
      installSecurityHeaders(production)
      installStatusPages()
      routing {
        get("/page") { call.respondText("a page") }
        get("/bug") { error("a bug") }
      }
    }
    block(client)
  }

  private fun HttpResponse.shouldCarrySecurityHeaders() =
    withClue(call.request.url.encodedPath) {
      headers["X-Content-Type-Options"] shouldBe "nosniff"
      headers["Content-Security-Policy"] shouldBe "frame-ancestors 'none'"
      headers["Referrer-Policy"] shouldBe "same-origin"
    }

  init {
    "a page, a missing page, and an error all carry the security headers" {
      headersTest(production = false) { client ->
        client.get("/page").run {
          status shouldBe OK
          shouldCarrySecurityHeaders()
        }
        client.get("/nowhere").run {
          status shouldBe NotFound
          shouldCarrySecurityHeaders()
        }
        client.get("/bug").run {
          status shouldBe InternalServerError
          shouldCarrySecurityHeaders()
        }
      }
    }

    "HSTS is sent in production, and not to a local server reached over plain HTTP" {
      headersTest(production = true) { client ->
        client.get("/page").headers["Strict-Transport-Security"] shouldBe "max-age=31536000"
      }
      headersTest(production = false) { client ->
        client.get("/page").headers["Strict-Transport-Security"] shouldBe null
      }
    }
  }
}
