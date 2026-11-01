package com.pambrose.historywalk.auth

import com.pambrose.historywalk.Auth.AUTH_COOKIE
import com.pambrose.historywalk.User
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.cookie
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.util.AttributeKey
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * What checking a session needs from the user's row: the address the role rule reads, and the session version, 0
 * for a user who has never signed out.
 */
data class SessionUser(
  val email: String,
  val sessionVersion: Int = 0,
)

class SessionValidationConfig {
  var findUser: (UUID) -> SessionUser? = { User.sessionUserFor(it) }
  var teacherEmails: () -> Set<String> = { SchoolConfig.teacherEmails }
}

/**
 * Signs a browser out when its session cookie can no longer be trusted: older than [UserSession.LIFETIME], not a
 * UUID, a user who is gone or has signed out since, or an address the role rule no longer admits. Runs before routing,
 * with one lookup of the user ([SessionValidationConfig.findUser]) for each request that carries a session.
 */
val SessionValidation =
  createApplicationPlugin("SessionValidation", createConfiguration = ::SessionValidationConfig) {
    val findUser = pluginConfig.findUser
    val teacherEmails = pluginConfig.teacherEmails
    onCall { call ->
      val session = call.sessions.get<UserSession>() ?: return@onCall
      untrustedBecause(session, Clock.System.now(), findUser, teacherEmails())?.also { reason ->
        logger.info { "Signing out the session for user ${session.uuid}: $reason" }
        call.sessions.clear<UserSession>()
      }
    }
  }

/** Why [session] can no longer be trusted, or null while it can. */
fun untrustedBecause(
  session: UserSession?,
  now: Instant,
  findUser: (UUID) -> SessionUser?,
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
      val user = uuid?.let(findUser)
      when {
        uuid == null -> "the user id is not a UUID"
        user == null -> "the user no longer exists"
        user.sessionVersion != session.sessionVersion -> "the user has signed out since this sign-in"
        Roles.roleFor(user.email, teacherEmails) == null -> "the account is no longer allowed"
        else -> null
      }
    }
  }

/**
 * Where sign-in reads a user's session version and sign-out ends their sessions: the users table, or what a test put
 * in its place. [installUserSessions] keeps it in the application's attributes, so the sign-in and sign-out routes use
 * the same users as [SessionValidation] does without taking them as parameters.
 */
internal class SessionStore(
  val findUser: (UUID) -> SessionUser?,
  val endSessions: (UUID) -> Unit,
)

private val SessionStoreKey = AttributeKey<SessionStore>("SessionStore")

internal val ApplicationCall.sessionStore: SessionStore
  get() =
    application.attributes.getOrNull(SessionStoreKey)
      ?: error("No session store: installUserSessions() was not called")

/**
 * Installs the encrypted session cookie, then [SessionValidation] (after Sessions, so the session is loaded).
 * [findUser] reads a user's address and session version; [endSessions] moves the version on, at sign-out.
 */
fun Application.installUserSessions(
  secure: Boolean = SessionConfig.isProduction,
  findUser: (UUID) -> SessionUser? = { User.sessionUserFor(it) },
  teacherEmails: () -> Set<String> = { SchoolConfig.teacherEmails },
  endSessions: (UUID) -> Unit = { User.incrementSessionVersion(it) },
) {
  attributes.put(SessionStoreKey, SessionStore(findUser, endSessions))
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
    this.findUser = findUser
    this.teacherEmails = teacherEmails
  }
}
