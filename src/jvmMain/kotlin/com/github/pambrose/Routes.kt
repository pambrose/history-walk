package com.github.pambrose

import com.codahale.metrics.jvm.ThreadDump
import com.github.pambrose.DbmsTxs.deleteChoices
import com.github.pambrose.DbmsTxs.updateLastSlide
import com.github.pambrose.EndPoints.CONTENT_RESET
import com.github.pambrose.EndPoints.REASONS
import com.github.pambrose.EndPoints.SLIDE
import com.github.pambrose.EndPoints.SUMMARY
import com.github.pambrose.EndPoints.USER_RESET
import com.github.pambrose.EnvVar.ALLOW_SLIDE_ACCESS
import com.github.pambrose.HistoryWalkServer.loadSlides
import com.github.pambrose.HistoryWalkServer.masterSlides
import com.github.pambrose.Pages.displayAllSlides
import com.github.pambrose.Pages.displayUserReasons
import com.github.pambrose.Pages.displayUserSummary
import com.github.pambrose.auth.GOOGLE_AUTH
import com.github.pambrose.auth.SchoolConfig
import com.github.pambrose.auth.signInRoutes
import com.github.pambrose.auth.signedInApi
import com.github.pambrose.auth.teachersOnly
import com.github.pambrose.auth.userUuid
import com.github.pambrose.slides.SlideDeck.Companion.ROOT
import com.pambrose.common.response.respondWith
import com.pambrose.common.util.Version.Companion.versionDesc
import dev.kilua.rpc.applyRoutes
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.ContentType.Text.Plain
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.io.ByteArrayOutputStream
import java.lang.management.ManagementFactory
import java.util.UUID
import kotlin.text.Charsets.UTF_8

object Routes {
  private val logger = KotlinLogging.logger {}

  fun Application.assignRoutes() {
    routing { appRoutes() }
  }

  /**
   * Every route, by who may reach it:
   * - sign-in and sign-out, open to all ([signInRoutes]);
   * - the RPC services, for any signed-in user, refusing a call without a session with 401 ([signedInApi]);
   * - the user's own reset, for any signed-in user, sending a browser without a session to Google sign-in;
   * - the class pages and slide tools, for teachers only, refusing anyone else with 403 ([teachersOnly]);
   * - health, version, and static content, open to all.
   */
  fun Route.appRoutes(
    allowSlideAccess: Boolean = ALLOW_SLIDE_ACCESS.getEnv(false),
    findEmail: (UUID) -> String? = { User.emailForUuid(it) },
    teacherEmails: () -> Set<String> = { SchoolConfig.teacherEmails },
  ) {
    signInRoutes()

    signedInApi {
      authenticate(GOOGLE_AUTH) {
        applyRoutes(ContentServiceManager)
      }
    }

    authenticate(GOOGLE_AUTH) {
      get(USER_RESET) {
        transaction {
          val uuid = call.userUuid
          deleteChoices(uuid)
          updateLastSlide(uuid, ROOT)
        }
        call.respondRedirect("/")
      }

      teachersOnly(findEmail, teacherEmails) {
        get(SUMMARY) {
          respondWith { displayUserSummary() }
        }

        get(REASONS) {
          respondWith { displayUserReasons() }
        }

        get(CONTENT_RESET) {
          try {
            masterSlides = loadSlides()
            call.respondRedirect("/")
          } catch (e: Exception) {
            logger.error(e) { "Error resetting slides" }
            call.respondText(e.stackTraceToString(), Plain)
          }
        }

        if (allowSlideAccess) {
          get("$SLIDE/{slideId}/{version?}") {
            val slideId = call.parameters.getOrFail("slideId").toInt()
            val version =
              try {
                call.parameters.getOrFail("version").toInt()
              } catch (e: Exception) {
                0
              }
            val slide = masterSlides.findSlideById(slideId, version)
            if (slide != null) {
              transaction {
                val uuid = call.userUuid
                updateLastSlide(uuid, slide.pathName)
              }
              call.respondRedirect("/")
            } else {
              call.respondText("Slide not found: $slideId/$version", Plain)
            }
          }

          get("slides") {
            respondWith { displayAllSlides() }
          }
        }
      }
    }

    get("ping") {
      call.respondText("pong", Plain)
    }

    get("version") {
      call.respondText(HistoryWalkServer::class.versionDesc(), Plain)
    }

    get("threaddump") {
      try {
        ByteArrayOutputStream()
          .apply {
            use { ThreadDumpInfo.threadDump.dump(true, true, it) }
          }.let { baos ->
            String(baos.toByteArray(), UTF_8)
          }
      } catch (e: NoClassDefFoundError) {
        "Sorry, your runtime environment does not allow dump threads."
      }.also {
        call.respondText(it, Plain)
      }
    }

    // Allow for static content to be served from the /static/ directory
//    static("static") {
//      resources("static")
//    }
    staticResources("/static", "static")
  }

  private object ThreadDumpInfo {
    val threadDump by lazy { ThreadDump(ManagementFactory.getThreadMXBean()) }
  }
}
