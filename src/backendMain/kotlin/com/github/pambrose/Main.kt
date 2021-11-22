package com.github.pambrose

import com.github.pambrose.ConfigureFormAuth.configureFormAuth
import com.github.pambrose.Cookies.assignCookies
import com.github.pambrose.Property.Companion.assignProperties
import com.github.pambrose.Routes.assignRoutes
import com.github.pambrose.common.script.KotlinScript
import com.github.pambrose.common.util.GitHubFile
import com.github.pambrose.common.util.GitHubRepo
import com.github.pambrose.common.util.OwnerType
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
  var masterSlides = AtomicReference(SlideContent())
}

fun loadSlides(): SlideContent {
  val logger = KotlinLogging.logger {}
  try {
    return runBlocking {

      val repoType = EnvVar.SLIDES_REPO_TYPE.getEnv("User")
      val gh = GitHubFile(
        GitHubRepo(
          if (repoType.equals("User", ignoreCase = true)) OwnerType.User else OwnerType.Organization,
          EnvVar.SLIDES_REPO_OWNER.getEnv("pambrose"),
          EnvVar.SLIDES_REPO_NAME.getEnv("history-walk")
        ),
        branchName = EnvVar.SLIDES_BRANCH.getEnv("master"),
        srcPath = EnvVar.SLIDES_PATH.getEnv("src/backendMain/kotlin"),
        fileName = EnvVar.SLIDES_FILENAME.getEnv("Slides.kt")
      )

//    val fs = FileSystemSource("./").file("../src/backendMain/kotlin/$fileName")

      val varName = EnvVar.DBMS_DRIVER_VARIABLE_NAME.getEnv("slides")
      val code = "${gh.content}\n\n$varName"

      KotlinScript().use { it.eval(code) as SlideContent }.apply { validate() }
    }
  } catch (e: Throwable) {
    logger.error(e) { "Failed to load slides" }
    throw e
  }
}

fun Application.main() {
  val logger = KotlinLogging.logger {}

  logger.apply {
    info { getBanner("banners/historywalk.txt", this) }
    info { HistoryWalkServer::class.versionDesc() }
//    callerVersion = callerVersion(args)
//    info { "Caller Version: $callerVersion" }
  }

  assignProperties()

  HistoryWalkServer.masterSlides.set(loadSlides())

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
