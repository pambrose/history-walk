package com.github.pambrose

import com.github.pambrose.User.Companion.isValidUuid
import com.github.pambrose.common.util.isNotNull
import com.github.pambrose.common.util.isNull
import com.github.pambrose.common.util.sha256
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.auth.*
import io.ktor.server.sessions.*

object ConfigureFormAuth {
  private val logger = KotlinLogging.logger {}

  fun AuthenticationConfig.configureFormAuth() {
    form {
      userParamName = "username"
      passwordParamName = "password"

      validate { cred ->
        logger.info { "validate() called" }
        var principal: UserPrincipal? = null
        val user = User.queryUserByEmail(Email(cred.name))
        if (user.isNotNull()) {
          val salt = user.salt
          val digest = user.digest
          if (salt.isNotBlank() && digest.isNotBlank() && digest == cred.password.sha256(salt)) {
            logger.debug { "Found user ${cred.name} ${user.uuid}" }
            principal = UserPrincipal(user.uuid)
          }
        }

        logger.info { "Login ${if (principal.isNull()) "failure" else "success for $user ${user?.email ?: UNKNOWN}"}" }

        principal
      }

      skipWhen { call ->
        call.sessions.get<UserId>()?.uuid?.let { uuid ->
          isValidUuid(uuid)
        } ?: false
      }
    }
  }
}
