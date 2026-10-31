package com.github.pambrose

import com.github.pambrose.auth.configureGoogleAuth
import com.github.pambrose.auth.installUserSessions
import com.pambrose.common.util.simpleClassName
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import org.slf4j.event.Level

object Installs {
  private val logger = KotlinLogging.logger {}

  fun Application.installs() {
    install(Compression)
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
