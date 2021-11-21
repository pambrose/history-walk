package com.github.pambrose

import com.github.pambrose.ConfigureFormAuth.configureFormAuth
import com.github.pambrose.Cookies.assignCookies
import com.github.pambrose.Property.Companion.assignProperties
import com.github.pambrose.Routes.assignRoutes
import com.github.pambrose.common.script.KotlinScript
import com.github.pambrose.common.util.FileSystemSource
import com.github.pambrose.common.util.UrlSource
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
import java.util.concurrent.atomic.AtomicReference

//@Version(version = BuildConfig.CORE_VERSION, date = BuildConfig.CORE_RELEASE_DATE)
object HistoryWalkServer : KLogging() {

}

fun loadSlides() =
  runBlocking {
    val remote = UrlSource("https://raw.githubusercontent.com/pambrose/slides/master/slides.json")
    val fs = FileSystemSource("./").file("../src/backendMain/kotlin/Slides.kt")
    val code = "${fs.content}\n\nslides"
    KotlinScript().use { it.eval(code) as SlideContent }.apply { validate() }
  }

var masterSlides = AtomicReference(SlideContent())

fun Application.main() {
  val logger = KotlinLogging.logger {}

  logger.apply {
    info { getBanner("banners/historywalk.txt", this) }
    info { HistoryWalkServer::class.versionDesc() }
//    callerVersion = callerVersion(args)
//    info { "Caller Version: $callerVersion" }
  }

  assignProperties()

  masterSlides.set(loadSlides())

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
