package com.github.pambrose

import com.github.pambrose.ConfigureFormAuth.configureFormAuth
import com.github.pambrose.Cookies.assignCookies
import com.github.pambrose.EnvVar.*
import com.github.pambrose.HistoryWalkServer.loadSlides
import com.github.pambrose.HistoryWalkServer.masterSlides
import com.github.pambrose.Property.Companion.assignProperties
import com.github.pambrose.Routes.assignRoutes
import com.github.pambrose.common.script.KotlinScript
import com.github.pambrose.common.util.*
import com.github.pambrose.common.util.Version.Companion.versionDesc
import com.github.pambrose.slides.SlideDeck
import io.ktor.http.*
import io.ktor.http.ContentType.Text.Plain
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.kvision.remote.kvisionInit
import kotlinx.coroutines.runBlocking
import mu.two.KLogging
import mu.two.KotlinLogging
import org.slf4j.event.Level
import java.io.File
import java.util.concurrent.atomic.AtomicReference

// @Version(version = BuildConfig.CORE_VERSION, date = BuildConfig.CORE_RELEASE_DATE)
object HistoryWalkServer : KLogging() {
  private val masterSlidesRef = AtomicReference(SlideDeck())

  var masterSlides: SlideDeck
    get() = masterSlidesRef.get()
    set(value) = masterSlidesRef.set(value)

  fun loadSlides() =
    try {
      runBlocking {
        val src =
          SLIDES_LOCAL_FILENAME.getEnv("")
            .let { localFilename ->
              if (localFilename.isNotEmpty()) {
                logger.info { "Loading slides from local file: $localFilename" }
                // find out current working directory
                val currentWorkingDir = System.getProperty("user.dir")
                println(currentWorkingDir)
                val file = File("$localFilename")
                file.readText()
              } else {
                val repoType = SLIDES_REPO_TYPE.getEnv("User")
                val gh = GitHubFile(
                  GitHubRepo(
                    if (repoType.equals("User", ignoreCase = true)) OwnerType.User else OwnerType.Organization,
                    SLIDES_REPO_OWNER.getEnv("pambrose"),
                    SLIDES_REPO_NAME.getEnv("history-walk-content"),
                  ),
                  branchName = SLIDES_REPO_BRANCH.getEnv("master"),
                  srcPath = SLIDES_REPO_PATH.getEnv("src/main/kotlin"),
                  fileName = SLIDES_REPO_FILENAME.getEnv("Slides.kt"),
                )
                logger.info { "Loading slides from GitHub: $gh" }
                gh.content
              }
            }

        val varName = SLIDES_VARIABLE_NAME.getEnv("slides")
        val code = "$src\n\n$varName"

        KotlinScript().use { it.eval(code) as SlideDeck }
      }
    } catch (e: Throwable) {
      logger.error(e) { "Failed to load slides" }
      throw e
    }
}

fun Application.main() {
  val logger = KotlinLogging.logger {}

  logger.apply {
    info {
      getBanner(
        "banners/history-walk.txt",
        this,
      )
    }
    info { HistoryWalkServer::class.versionDesc() }
//    callerVersion = callerVersion(args)
//    info { "Caller Version: $callerVersion" }
  }

  assignProperties()

  masterSlides = loadSlides()

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

  Dbms.init(environment.config)

  install(Authentication) {
    configureFormAuth()
  }

  install(StatusPages) {
    // Catch all
    exception<Throwable> { call, cause ->
      logger.info(cause) { " Throwable caught: ${cause.simpleClassName}" }
      call.respond(NotFound)
    }

    status(NotFound) { call, code ->
      call.respond(
        TextContent(
          "${code.value} ${code.description}",
          Plain.withCharset(Charsets.UTF_8),
          code,
        ),
      )
    }
  }

  routing {
    assignRoutes()

    // Allow for static content to be served from the /static/ directory
    static("static") {
      resources("static")
    }
  }

  kvisionInit()
}
