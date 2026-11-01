package com.pambrose.historywalk.auth

import com.pambrose.historywalk.Utils.toUuid
import io.ktor.server.application.ApplicationCall
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * The signed-in user, held in an encrypted and signed cookie. [created] is required (no default) so it is always
 * written: a default equal to the value at sign-in would be dropped from the cookie and read back as the time of each
 * request, and the session would never expire.
 *
 * [sessionVersion] is the user's session version at sign-in (`users.session_version`). Signing out moves the stored
 * one on, and [SessionValidation] signs out a session whose version differs. Its default can be the constant 0,
 * unlike [created]'s: a version of 0 is left out of the cookie and read back as 0, and a cookie written before
 * versions existed has none and reads as 0 too, the version every user started at, so such a session stays valid
 * until the user next signs out.
 */
@Serializable
data class UserSession(
  val uuid: String,
  val created: Long,
  val sessionVersion: Int = 0,
) {
  /** Whether this sign-in is older than [LIFETIME] at [now]. */
  fun isExpiredAt(now: Instant): Boolean = now - Instant.fromEpochMilliseconds(created) > LIFETIME

  companion object {
    /**
     * How long a sign-in lasts: a school day, so a session left open on a shared lab computer is gone by the next
     * morning. The cookie's max age only asks the browser to discard it; the server enforces it too.
     */
    val LIFETIME: Duration = 12.hours
  }
}

/** The signed-in user's id, or null when there is no session or it does not hold a UUID. */
fun ApplicationCall.signedInUserUuid(): String? =
  sessions.get<UserSession>()?.uuid?.takeIf { runCatching { it.toUuid() }.isSuccess }

/** The signed-in user's id. Only for use behind guards that have already required a session. */
val ApplicationCall.userUuid: String get() = signedInUserUuid() ?: error("Missing UserSession")

/**
 * Signs [uuid] in by setting the session cookie, carrying the user's current session version, so the session lasts
 * until [UserSession.LIFETIME] passes or the user signs out, wherever that happens.
 */
fun ApplicationCall.assignUserSession(uuid: UUID) {
  val version = sessionStore.findUser(uuid)?.sessionVersion ?: 0
  sessions.set(UserSession(uuid.toString(), Clock.System.now().toEpochMilliseconds(), version))
}

/**
 * Signs the user out in every browser, not just this one: the stored session version moves on, so every cookie issued
 * before now, copies included, is no longer trusted ([untrustedBecause]), and this browser's cookie is cleared. A
 * browser without a trusted session has its cookie cleared and nothing else.
 */
fun ApplicationCall.signOutEverywhere() {
  signedInUserUuid()?.let { sessionStore.endSessions(it.toUuid()) }
  sessions.clear<UserSession>()
}
