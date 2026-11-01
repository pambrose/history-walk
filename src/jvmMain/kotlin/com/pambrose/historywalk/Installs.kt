package com.pambrose.historywalk

import com.pambrose.common.util.simpleClassName
import com.pambrose.historywalk.auth.configureGoogleAuth
import com.pambrose.historywalk.auth.installUserSessions
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.conditionalheaders.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import org.slf4j.event.Level

object Installs {
  private val logger = KotlinLogging.logger {}

  /**
   * Answers a request for content the browser already holds with 304 and no body. The static content carries an ETag
   * (`Routes.revalidatedEachTime`), so a browser that checks the bundle on every page load downloads it again only once
   * a release has changed it.
   */
  internal fun Application.installConditionalHeaders() {
    install(ConditionalHeaders)
  }

  /**
   * Answers a HEAD request with the headers its GET gets, and no body. Ktor answers HEAD only where a route says so, so
   * without this `curl -sI`, which is how a release is checked through Cloudflare (`cf-cache-status`), gets a 404, and
   * Cloudflare can pass that 404 on in place of the bundle's headers.
   */
  internal fun Application.installAutoHeadResponse() {
    install(AutoHeadResponse)
  }

  fun Application.installs() {
    install(Compression)
    installConditionalHeaders()
    installAutoHeadResponse()
    install(DefaultHeaders)
    install(CallLogging) {
      level = Level.INFO
      // Log status, method, and path only: the default format appends a redirect's Location, which for /auth/login is
      // the Google URL with the OAuth state
      format { call -> "${call.response.status()}: ${call.request.httpMethod.value} ${call.request.path()}" }
    }

    installUserSessions()

//  install(KHealth) {
//    readyCheckEnabled = false
//
//    healthChecks {
//      check("Server ready") { true }
//    }
//  }

    install(Authentication) { configureGoogleAuth() }

    install(StatusPages) {
      // Catch all
      exception<Throwable> { call, cause ->
        logger.info(cause) { " Throwable caught: ${cause.simpleClassName}" }
        call.respond(HttpStatusCode.NotFound)
      }

      status(HttpStatusCode.NotFound) { call, code ->
        call.respond(
          TextContent(
            "${code.value} ${code.description}",
            ContentType.Text.Plain.withCharset(Charsets.UTF_8),
            code,
          ),
        )
      }
    }
  }
}
