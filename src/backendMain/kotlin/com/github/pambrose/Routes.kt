package com.github.pambrose

import com.codahale.metrics.jvm.ThreadDump
import com.github.pambrose.Content.ROOT
import com.github.pambrose.ContentService.Companion.deleteChoices
import com.github.pambrose.ContentService.Companion.updateLastTitle
import com.github.pambrose.EndPoints.LOGIN
import com.github.pambrose.EndPoints.LOGOUT
import com.github.pambrose.common.util.Version.Companion.versionDesc
import com.github.pambrose.common.util.isNotNull
import io.ktor.application.call
import io.ktor.auth.authenticate
import io.ktor.auth.principal
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.response.respondRedirect
import io.ktor.response.respondText
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.sessions.clear
import io.ktor.sessions.sessions
import io.ktor.sessions.set
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