package com.pambrose.historywalk

import com.pambrose.historywalk.Dbms.dbmsInit
import com.pambrose.historywalk.EnvVar.*
import com.pambrose.historywalk.HistoryWalkServer.masterSlides
import com.pambrose.historywalk.Installs.installs
import com.pambrose.historywalk.Property.Companion.assignProperties
import com.pambrose.historywalk.Routes.assignRoutes
import com.pambrose.historywalk.auth.verifyAuthConfig
import com.pambrose.historywalk.slides.SlideDeck
import com.pambrose.common.script.KotlinScript
import com.pambrose.common.util.GitHubFile
import com.pambrose.common.util.GitHubRepo
import com.pambrose.common.util.OwnerType.Organization
import com.pambrose.common.util.OwnerType.User
import com.pambrose.common.util.Version.Companion.versionDesc
import com.pambrose.common.util.getBanner
import dev.kilua.rpc.initRpc
import dev.kilua.rpc.registerService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.*
import com.pambrose.historywalk.content.jermainLonguenSlides
import kotlinx.coroutines.runBlocking
import com.pambrose.historywalk.content.mosesSlides
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
                // find out the current working directory
                val currentWorkingDir = System.getProperty("user.dir")
                logger.info {"currentWorkingDir: $currentWorkingDir"}
                val file = File(localFilename)
                file.readText()
              } else {
                val repoType = SLIDES_REPO_TYPE.getEnv("User")
                val gh = GitHubFile(
                  repo = GitHubRepo(
                    ownerType = if (repoType.equals("User", ignoreCase = true)) User else Organization,
                    ownerName = SLIDES_REPO_OWNER.getEnv("pambrose"),
                    repoName = SLIDES_REPO_NAME.getEnv("history-walk-content"),
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

  // Fails startup before anything is installed if a required sign-in setting is missing
  verifyAuthConfig()

  installs()
  assignRoutes()

  val defaultSlides = "jermainLonguenSlides"

  val slideName = SLIDES_VARIABLE_NAME.getEnv(defaultSlides)
  masterSlides =
    if (slideName == defaultSlides)
      jermainLonguenSlides
    else
      mosesSlides
  // masterSlides = loadSlides()

  dbmsInit(environment.config)

  initRpc {
    registerService<IContentService> { call -> ContentService(call) }
  }
}
