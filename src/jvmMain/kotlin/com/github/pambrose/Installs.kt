package com.github.pambrose

import com.github.pambrose.ConfigureFormAuth.configureFormAuth
import com.github.pambrose.Cookies.assignCookies
import com.github.pambrose.common.util.simpleClassName
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.sessions.*
import org.slf4j.event.Level

object Installs {
  private val logger = KotlinLogging.logger {}

  fun Application.installs() {
    install(Compression)
    install(DefaultHeaders)
    install(CallLogging) {
      level = Level.INFO
    }

    install(Sessions) {
      assignCookies()
    }

//  install(KHealth) {
//    readyCheckEnabled = false
//
//    healthChecks {
//      check("Server ready") { true }
//    }
//  }

    install(Authentication) {
      configureFormAuth()
    }

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
