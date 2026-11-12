package com.pambrose.historywalk

import com.pambrose.common.util.simpleClassName
import com.pambrose.historywalk.auth.configureGoogleAuth
import com.pambrose.historywalk.auth.installUserSessions
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.autohead.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.conditionalheaders.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import org.slf4j.event.Level
import java.sql.SQLException

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

    installStatusPages()
  }

  /**
   * What a request that fails is answered with. A bad parameter is a 400, and a path or route with nothing there a 404.
   * Anything else is the server's fault: a 503 when the database is behind it (the session check reads the user on
   * every request that carries a session, the page shell's included, so an outage reaches every signed-in page), and a
   * 500 otherwise, each logged at ERROR with its stack trace. The log names the method and path only: a callback's query
   * carries Google's authorization code and the OAuth state. The RPC calls never get here: Kilua answers their failures
   * itself.
   */
  internal fun Application.installStatusPages() {
    install(StatusPages) {
      exception<BadRequestException> { call, cause ->
        logger.warn { "${call.request.httpMethod.value} ${call.request.path()}: ${cause.message}" }
        call.respondStatus(HttpStatusCode.BadRequest)
      }

      exception<NotFoundException> { call, _ ->
        call.respondStatus(HttpStatusCode.NotFound)
      }

      exception<Throwable> { call, cause ->
        val status =
          if (cause.isDatabaseFailure()) HttpStatusCode.ServiceUnavailable else HttpStatusCode.InternalServerError
        logger.error(cause) {
          val request = "${call.request.httpMethod.value} ${call.request.path()}"
          "$request failed with ${cause.simpleClassName}: answered ${status.value}"
        }
        call.respondStatus(status)
      }

      status(HttpStatusCode.NotFound) { call, code -> call.respondStatus(code) }
    }
  }

  /** What a page that failed says, under its status code: `404 Not Found`, or a 5xx with what to do about it. */
  internal fun statusText(code: HttpStatusCode): String =
    "${code.value} ${code.description}" +
      when (code) {
        HttpStatusCode.ServiceUnavailable -> ": the database is not answering. Please try again in a minute."
        HttpStatusCode.InternalServerError -> ": something went wrong. Please try again."
        else -> ""
      }

  private suspend fun ApplicationCall.respondStatus(code: HttpStatusCode) =
    respond(TextContent(statusText(code), ContentType.Text.Plain.withCharset(Charsets.UTF_8), code))

  /** Whether [this] failed for want of the database: a JDBC error (Exposed's and Hikari's included) in its causes. */
  internal fun Throwable.isDatabaseFailure(): Boolean =
    generateSequence(this) { it.cause }.take(MAX_CAUSES).any { it is SQLException }

  // How far down a chain of causes isDatabaseFailure looks, so a cause that leads back to itself cannot loop for ever
  private const val MAX_CAUSES = 10
}
