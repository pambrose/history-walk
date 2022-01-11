package com.github.pambrose

import com.codahale.metrics.jvm.ThreadDump
import com.github.pambrose.DbmsTxs.deleteChoices
import com.github.pambrose.DbmsTxs.updateLastSlide
import com.github.pambrose.EndPoints.CONTENT_RESET
import com.github.pambrose.EndPoints.LOGIN
import com.github.pambrose.EndPoints.LOGOUT
import com.github.pambrose.EndPoints.SLIDE
import com.github.pambrose.EndPoints.USER_RESET
import com.github.pambrose.EnvVar.ALLOW_SLIDE_ACCESS
import com.github.pambrose.HistoryWalkServer.loadSlides
import com.github.pambrose.HistoryWalkServer.masterSlides
import com.github.pambrose.Pages.displayAllSlides
import com.github.pambrose.Pages.displayUserSummary
import com.github.pambrose.common.response.respondWith
import com.github.pambrose.common.util.Version.Companion.versionDesc
import com.github.pambrose.common.util.isNotNull
import com.github.pambrose.slides.SlideDeck.Companion.ROOT
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.http.ContentType.Text.Plain
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.sessions.*
import io.ktor.util.*
import io.kvision.remote.applyRoutes
import mu.KLogging
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.ByteArrayOutputStream
import java.lang.management.ManagementFactory
import kotlin.text.Charsets.UTF_8

object Routes : KLogging() {

  fun Route.assignRoutes() {
    applyRoutes(RegisterUserServiceManager)

    authenticate {
      applyRoutes(ContentServiceManager)

      post(LOGIN) {
        val principal = call.principal<UserPrincipal>()
        val result =
          if (principal != null) {
            val user = User.queryUserByUuid(principal.uuid)
            if (user.isNotNull()) {
              val userId = UserId(user.uuid.toString())
              call.sessions.set(userId)
              HttpStatusCode.OK
            }
            else {
              HttpStatusCode.Unauthorized
            }
          }
          else {
            HttpStatusCode.Unauthorized
          }

        call.respond(result)
      }

      get(LOGOUT) {
        call.sessions.clear<UserId>()
        call.respondRedirect("/")
      }

      get(CONTENT_RESET) {
        try {
          masterSlides = loadSlides()
          call.respondRedirect("/")
        } catch (e: Exception) {
          logger.error("Error resetting slides", e)
          call.respondText(e.stackTraceToString(), Plain)
        }
      }

      get(USER_RESET) {
        transaction {
          val uuid = call.userId.uuid
          deleteChoices(uuid)
          updateLastSlide(uuid, ROOT)
        }
        call.respondRedirect("/")
      }

      get("summary") {
        respondWith { displayUserSummary() }
      }

      if (ALLOW_SLIDE_ACCESS.getEnv(false)) {
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
              val uuid = call.userId.uuid
              updateLastSlide(uuid, slide.pathName)
            }
            call.respondRedirect("/")
          }
          else {
            call.respondText("Slide not found: $slideId/$version", Plain)
          }
        }

        get("slides") {
          respondWith { displayAllSlides() }
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
  }

  private object ThreadDumpInfo {
    val threadDump by lazy { ThreadDump(ManagementFactory.getThreadMXBean()) }
  }
}