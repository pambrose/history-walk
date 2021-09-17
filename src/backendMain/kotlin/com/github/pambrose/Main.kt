package com.github.pambrose

import com.github.pambrose.ConfigureFormAuth.configureFormAuth
import com.github.pambrose.Cookies.assignCookies
import com.github.pambrose.Property.Companion.assignProperties
import com.github.pambrose.Routes.assignRoutes
import com.github.pambrose.common.util.getBanner
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.features.*
import io.ktor.routing.*
import io.ktor.sessions.*
import io.ktor.util.pipeline.*
import io.kvision.remote.kvisionInit
import mu.KotlinLogging

fun Application.main() {
  val logger = KotlinLogging.logger {}

  logger.apply {
    info { getBanner("banners/historywalk.txt", this) }
//    info { ReadingBatServer::class.versionDesc() }
//    callerVersion = callerVersion(args)
//    info { "Caller Version: $callerVersion" }
  }

  assignProperties()

  SlideContent.initContent()
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

val ApplicationCall.userPrincipal get() = sessions.get<UserPrincipal>()
