package com.pambrose.historywalk.auth

import com.pambrose.historywalk.Auth.AUTH_COOKIE
import com.pambrose.historywalk.User
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.Application
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.cookie
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Instant

private val logger = KotlinLogging.logger {}

class SessionValidationConfig {
  var findEmail: (UUID) -> String? = { User.emailForUuid(it) }
  var teacherEmails: () -> Set<String> = { SchoolConfig.teacherEmails }
}

/**
 * Signs a browser out when its session cookie can no longer be trusted: older than [UserSession.LIFETIME], not a
 * UUID, a user who is gone, or an address the role rule no longer admits. Runs before routing.
 */
val SessionValidation =
  createApplicationPlugin("SessionValidation", createConfiguration = ::SessionValidationConfig) {
    val findEmail = pluginConfig.findEmail
    val teacherEmails = pluginConfig.teacherEmails
    onCall { call ->
      val session = call.sessions.get<UserSession>() ?: return@onCall
      untrustedBecause(session, Clock.System.now(), findEmail, teacherEmails())?.also { reason ->
        logger.info { "Signing out the session for user ${session.uuid}: $reason" }
        call.sessions.clear<UserSession>()
      }
    }
  }

/** Why [session] can no longer be trusted, or null while it can. */
fun untrustedBecause(
  session: UserSession?,
  now: Instant,
  findEmail: (UUID) -> String?,
  teacherEmails: Set<String>,
): String? =
  when {
    session == null -> {
      "there is no user session"
    }

    session.isExpiredAt(now) -> {
      "signed in more than ${UserSession.LIFETIME} ago"
    }

    else -> {
      val uuid = runCatching { UUID.fromString(session.uuid) }.getOrNull()
      val email = uuid?.let(findEmail)
      when {
        uuid == null -> "the user id is not a UUID"
        email == null -> "the user no longer exists"
        Roles.roleFor(email, teacherEmails) == null -> "the account is no longer allowed"
        else -> null
      }
    }
  }

/** Installs the encrypted session cookie, then [SessionValidation] (after Sessions, so the session is loaded). */
fun Application.installUserSessions(
  secure: Boolean = SessionConfig.isProduction,
  findEmail: (UUID) -> String? = { User.emailForUuid(it) },
  teacherEmails: () -> Set<String> = { SchoolConfig.teacherEmails },
) {
  install(Sessions) {
    // In production the name takes the __Host- prefix, so no one but this host can plant it.
    cookie<UserSession>(hostCookieName(AUTH_COOKIE, secure)) {
      with(cookie) {
        path = "/"
        extensions["SameSite"] = "strict"
        maxAgeInSeconds = UserSession.LIFETIME.inWholeSeconds
        httpOnly = true
        this.secure = secure
      }
      transform(SessionConfig.encryptTransformer())
    }
  }
  install(SessionValidation) {
    this.findEmail = findEmail
    this.teacherEmails = teacherEmails
  }
}
