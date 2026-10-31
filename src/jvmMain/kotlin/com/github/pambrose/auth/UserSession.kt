package com.github.pambrose.auth

import com.github.pambrose.Utils.toUuid
import io.ktor.server.application.ApplicationCall
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * The signed-in user, held in an encrypted and signed cookie. [created] is required (no default) so it is always
 * written: a default equal to the value at sign-in would be dropped from the cookie and read back as the time of each
 * request, and the session would never expire.
 */
@Serializable
data class UserSession(
  val uuid: String,
  val created: Long,
) {
  /** Whether this sign-in is older than [LIFETIME] at [now]. */
  fun isExpiredAt(now: Instant): Boolean = now - Instant.fromEpochMilliseconds(created) > LIFETIME

  companion object {
    /** How long a sign-in lasts. The cookie's max age only asks the browser to discard it; the server enforces it too. */
    val LIFETIME: Duration = 14.days
  }
}

/** The signed-in user's id, or null when there is no session or it does not hold a UUID. */
fun ApplicationCall.signedInUserUuid(): String? =
  sessions.get<UserSession>()?.uuid?.takeIf { runCatching { it.toUuid() }.isSuccess }

/** The signed-in user's id. Only for use behind guards that have already required a session. */
val ApplicationCall.userUuid: String get() = signedInUserUuid() ?: error("Missing UserSession")

/** Signs [uuid] in by setting the session cookie. */
fun ApplicationCall.assignUserSession(uuid: UUID) {
  sessions.set(UserSession(uuid.toString(), Clock.System.now().toEpochMilliseconds()))
}
