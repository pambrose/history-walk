package com.github.pambrose

import com.github.pambrose.Content.ROOT
import com.github.pambrose.ContentService.Companion.deleteChoices
import com.github.pambrose.ContentService.Companion.updateLastTitle
import com.github.pambrose.EndPoints.LOGIN
import com.github.pambrose.EndPoints.LOGOUT
import com.github.pambrose.common.util.isNotNull
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.sessions.*
import io.kvision.remote.applyRoutes
import mu.KLogging
import org.jetbrains.exposed.sql.transactions.transaction

object Routes : KLogging() {

  fun Route.assignRoutes() {
    applyRoutes(RegisterUserServiceManager)

    authenticate {
      applyRoutes(ContentServiceManager)

      post(LOGIN) {
        logger.info { "/login called" }
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
        logger.info { "/logout called" }
        call.sessions.clear<UserId>()
        call.respondRedirect("/")
      }

    }

    get("reset") {
      logger.info { "/reset called" }
      transaction {
        val uuid = call.userId.uuid
        deleteChoices(uuid)
        updateLastTitle(uuid, ROOT)
      }
      call.respondRedirect("/")
    }
  }
}