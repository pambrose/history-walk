package com.github.pambrose

import com.github.pambrose.Dbms.dbmsInit
import com.github.pambrose.EnvVar.*
import com.github.pambrose.HistoryWalkServer.masterSlides
import com.github.pambrose.Installs.installs
import com.github.pambrose.Property.Companion.assignProperties
import com.github.pambrose.Routes.assignRoutes
import com.github.pambrose.common.script.KotlinScript
import com.github.pambrose.common.util.GitHubFile
import com.github.pambrose.common.util.GitHubRepo
import com.github.pambrose.common.util.OwnerType.Organization
import com.github.pambrose.common.util.OwnerType.User
import com.github.pambrose.common.util.Version.Companion.versionDesc
import com.github.pambrose.common.util.getBanner
import com.github.pambrose.slides.SlideDeck
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.*
import io.kvision.remote.kvisionInit
import jermainLonguenSlides
import kotlinx.coroutines.runBlocking
import mosesSlides
import java.io.File
import java.util.concurrent.atomic.AtomicReference

// @Version(version = BuildConfig.CORE_VERSION, date = BuildConfig.CORE_RELEASE_DATE)
object HistoryWalkServer {
  private val logger = KotlinLogging.logger {}
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
                    if (repoType.equals("User", ignoreCase = true)) User else Organization,
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

        logger.info { "Code top:\n${code.lines().take(10).joinToString("\n")}" }
        logger.info { "Code bottom:\n${code.lines().takeLast(10).joinToString("\n")}" }

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

  installs()
  assignRoutes()

  val slideName = SLIDES_VARIABLE_NAME.getEnv("jermainLonguenSlides")
  masterSlides =
    if (slideName == "jermainLonguenSlides")
      jermainLonguenSlides
    else
      mosesSlides
  // masterSlides = loadSlides()

  dbmsInit(environment.config)

  kvisionInit()
}
