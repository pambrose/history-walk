package com.github.pambrose

import com.github.pambrose.User.Companion.isValidUuid
import com.github.pambrose.common.util.isNotNull
import com.github.pambrose.common.util.isNull
import com.github.pambrose.common.util.sha256
import io.ktor.auth.*
import io.ktor.sessions.*
import mu.KLogging

object ConfigureFormAuth : KLogging() {
  fun Authentication.Configuration.configureFormAuth() {
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
        (call.sessions.get<UserId>()?.uuid?.let { uuid ->
          isValidUuid(uuid)
        } ?: false)
          .also { logger.info { "skipWhen called = $it" } }
      }
    }
  }
}