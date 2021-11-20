package com.github.pambrose

import com.github.pambrose.ConfigureFormAuth.configureFormAuth
import com.github.pambrose.Cookies.assignCookies
import com.github.pambrose.Property.Companion.assignProperties
import com.github.pambrose.Routes.assignRoutes
import com.github.pambrose.common.script.KotlinScript
import com.github.pambrose.common.util.FileSystemSource
import com.github.pambrose.common.util.Version.Companion.versionDesc
import com.github.pambrose.common.util.getBanner
import com.github.pambrose.slides.SlideContent
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.features.*
import io.ktor.routing.*
import io.ktor.sessions.*
import io.ktor.util.pipeline.*
import io.kvision.remote.kvisionInit
import kotlinx.coroutines.runBlocking
import mu.KLogging
import mu.KotlinLogging
import org.slf4j.event.Level

//@Version(version = BuildConfig.CORE_VERSION, date = BuildConfig.CORE_RELEASE_DATE)
object HistoryWalkServer : KLogging() {

}

var masterSlides = SlideContent()

fun Application.main() {
  val logger = KotlinLogging.logger {}

  logger.apply {
    info { getBanner("banners/historywalk.txt", this) }
    info { HistoryWalkServer::class.versionDesc() }
//    callerVersion = callerVersion(args)
//    info { "Caller Version: $callerVersion" }
  }

  assignProperties()

//  val path = Paths.get("").toAbsolutePath().toString()
//  println("cwd = $path")

  val fsSource = FileSystemSource("./")
  val fs = fsSource.file("../src/backendMain/kotlin/Slides.kt")
  val code = "${fs.content}\n\nslides"

  val slides = runBlocking {
    println(code)
    val l = KotlinScript().use { it.eval(code) as SlideContent }
    println(l)
    l
  }

  masterSlides = slides

  masterSlides.verifySlides()

  install(Compression)
  install(DefaultHeaders)
  install(CallLogging) {
    level = Level.INFO
  }

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
