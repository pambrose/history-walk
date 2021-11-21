package com.github.pambrose

import com.codahale.metrics.jvm.ThreadDump
import com.github.pambrose.Content.ROOT
import com.github.pambrose.ContentService.Companion.deleteChoices
import com.github.pambrose.ContentService.Companion.updateLastTitle
import com.github.pambrose.EndPoints.LOGIN
import com.github.pambrose.EndPoints.LOGOUT
import com.github.pambrose.common.util.Version.Companion.versionDesc
import com.github.pambrose.common.util.isNotNull
import com.github.pambrose.slides.SlideContent
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.sessions.*
import io.kvision.remote.applyRoutes
import mu.KLogging
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.ByteArrayOutputStream
import java.lang.management.ManagementFactory

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
            } else {
              HttpStatusCode.Unauthorized
            }
          } else {
            HttpStatusCode.Unauthorized
          }

        call.respond(result)
      }

      get("/reset") {
        masterSlides = SlideContent.loadSlides()
        call.respondRedirect("/")
      }

      get(LOGOUT) {
        call.sessions.clear<UserId>()
        call.respondRedirect("/")
      }
    }

    get("reset") {
      transaction {
        val uuid = call.userId.uuid
        deleteChoices(uuid)
        updateLastTitle(uuid, ROOT)
      }
      call.respondRedirect("/")
    }

    get("ping") {
      call.respondText("pong", ContentType.Text.Plain)
    }

    get("version") {
      call.respondText(HistoryWalkServer::class.versionDesc(), ContentType.Text.Plain)
    }

    get("threaddump") {
      try {
        ByteArrayOutputStream()
          .apply {
            use { ThreadDumpInfo.threadDump.dump(true, true, it) }
          }.let { baos ->
            String(baos.toByteArray(), Charsets.UTF_8)
          }
      } catch (e: NoClassDefFoundError) {
        "Sorry, your runtime environment does not allow dump threads."
      }.also {
        call.respondText(it, ContentType.Text.Plain)
      }
    }
  }

  private object ThreadDumpInfo {
    val threadDump by lazy { ThreadDump(ManagementFactory.getThreadMXBean()) }
  }
}