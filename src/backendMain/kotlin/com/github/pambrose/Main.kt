package com.github.pambrose

import com.github.pambrose.ConfigureFormAuth.configureFormAuth
import com.github.pambrose.Cookies.assignCookies
import com.github.pambrose.Property.Companion.assignProperties
import com.github.pambrose.Routes.assignRoutes
import com.github.pambrose.common.util.isNull
import com.github.pambrose.common.util.randomId
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.features.*
import io.ktor.routing.*
import io.ktor.sessions.*
import io.ktor.util.pipeline.*
import io.kvision.remote.kvisionInit
import mu.KLogging

const val AUTH_COOKIE = "auth"

fun Application.main() {

  assignProperties()

  Content.initContent()
  Slide.verifySlides()

  install(Compression)
  install(DefaultHeaders)
  install(CallLogging)

  install(Sessions) {
    assignCookies()
  }

  Db.init(environment.config)

  install(Authentication) {
    configureFormAuth()
  }

  routing {
    assignRoutes()
  }

  kvisionInit()
}

typealias PipelineCall = PipelineContext<Unit, ApplicationCall>

object BrowserSessions : KLogging() {
  fun PipelineCall.assignBrowserSession() {
//  if (call.request.headers.contains(NO_TRACK_HEADER))
//    return

    if (call.browserSession.isNull()) {
      val browserSession = BrowserSession(id = randomId(15))
      call.sessions.set(browserSession)

//    if (isSaveRequestsEnabled()) {
//      val ipAddress = call.request.origin.remoteHost
//      try {
//        lookupGeoInfo(ipAddress)
//      } catch (e: Throwable) {
//        logger.warn(e) {}
//      }
//    }

      logger.debug { "Created browser session: ${browserSession.id} - ${call.request.origin.remoteHost}" }
    }
  }
}
