package com.pambrose.historywalk

import com.pambrose.historywalk.Installs.installAutoHeadResponse
import com.pambrose.historywalk.Installs.installConditionalHeaders
import com.pambrose.historywalk.Routes.staticContent
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.NotModified
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.server.application.install
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication

// The real client is only in the jar; src/jvmTest/resources/assets holds stand-ins for its page shell and bundle
private const val BUNDLE = "/main.bundle.js"
private const val IMAGE = "/static/images/mansion.jpg"

/**
 * `main.bundle.js` has the same name in every release, so Cloudflare (or a browser) must not go on serving a copy of an
 * old one. The page shell, the bundle, and the images all tell the browser to check each time and a shared cache to
 * keep no copy, and their ETag makes the check a 304 until a release changes the file. The server is set up as
 * `Installs.installs` sets it up, with compression, conditional headers, and HEAD answered like GET.
 */
class StaticContentCachingTest : StringSpec() {
  private fun withServer(block: suspend ApplicationTestBuilder.() -> Unit) =
    testApplication {
      application {
        install(Compression)
        installConditionalHeaders()
        installAutoHeadResponse()
        routing { staticContent() }
      }
      block()
    }

  init {
    "the bundle and the images tell the browser to check each time, and a shared cache to keep no copy" {
      withServer {
        for (path in listOf(BUNDLE, IMAGE)) {
          val response = client.get(path)
          response.status shouldBe OK
          response.headers[HttpHeaders.CacheControl] shouldBe "no-cache, private"
          response.headers[HttpHeaders.ETag].orEmpty() shouldStartWith "\""
        }
      }
    }

    "the page shell at / is checked each time too" {
      withServer {
        val response = client.get("/")
        response.status shouldBe OK
        response.headers[HttpHeaders.CacheControl] shouldBe "no-cache, private"
        response.bodyAsText() shouldContain "kvapp"
      }
    }

    // A browser asks for gzip, which the server compresses the bundle and the page shell with
    "content the browser already holds is answered 304 with no body, compressed or not" {
      withServer {
        for (path in listOf(BUNDLE, "/", IMAGE)) {
          for (encoding in listOf(null, "gzip")) {
            val first = client.get(path) { encoding?.let { header(HttpHeaders.AcceptEncoding, it) } }
            val etag = first.headers[HttpHeaders.ETag]
            etag shouldNotBe null

            val again =
              client.get(path) {
                encoding?.let { header(HttpHeaders.AcceptEncoding, it) }
                header(HttpHeaders.IfNoneMatch, etag)
              }
            again.status shouldBe NotModified
            again.bodyAsBytes().size shouldBe 0
          }
        }
      }
    }

    "content a release has changed is sent again in full" {
      withServer {
        val response = client.get(BUNDLE) { header(HttpHeaders.IfNoneMatch, "\"from-an-earlier-release\"") }
        response.status shouldBe OK
        response.bodyAsText() shouldContain "History Walk version"
      }
    }

    // curl -sI, which is how a release is checked through Cloudflare, sends HEAD
    "a HEAD request gets the headers a GET does, and no body" {
      withServer {
        val get = client.get(BUNDLE)
        val head = client.head(BUNDLE)
        head.status shouldBe OK
        head.headers[HttpHeaders.CacheControl] shouldBe "no-cache, private"
        head.headers[HttpHeaders.ETag] shouldBe get.headers[HttpHeaders.ETag]
        head.bodyAsBytes().size shouldBe 0
      }
    }

    // Kilua's static handling answered every unknown path with the page shell
    "an unknown path is a 404, not the page shell" {
      withServer {
        client.get("/no-such-page").status shouldBe NotFound
      }
    }
  }
}
