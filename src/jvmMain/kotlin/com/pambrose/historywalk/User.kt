package com.pambrose.historywalk

import com.pambrose.common.exposed.get
import com.pambrose.common.util.*
import com.pambrose.historywalk.Utils.toUuid
import com.pambrose.historywalk.auth.Roles.roleFor
import com.pambrose.historywalk.auth.SchoolConfig
import com.pambrose.historywalk.auth.SessionUser
import com.pambrose.historywalk.dbms.UsersTable
import com.pambrose.historywalk.slides.Slide
import com.pambrose.historywalk.slides.SlideDeck
import com.pambrose.historywalk.slides.SlideDeck.Companion.ROOT
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.*

/**
 * The users table's queries, by user. Signed-in users are known by their id (a UUID) and their lowercased address;
 * nothing holds a user in memory between requests.
 */
class User private constructor() {
  companion object {
    private val logger = KotlinLogging.logger {}

    fun upsertFromGoogle(
      email: String,
      fullName: String,
      pictureUrl: String?,
      role: Role,
    ): UUID {
      val address = email.lowercase()
      val now = java.time.LocalDateTime
        .now()
        .toKotlinLocalDateTime()
      return transaction {
        with(UsersTable) {
          val existing = select(id).where { this@with.email eq address }.map { it[id].value }.firstOrNull()
          if (existing != null) {
            // The session version is left alone: signing in on one computer does not sign the user out of another
            update({ id eq existing }) { row ->
              row[this.fullName] = fullName.maxLength(128)
              row[this.pictureUrl] = pictureUrl
              row[this.role] = role.name.lowercase()
              row[lastSignIn] = now
              row[updated] = now
            }
            existing
          } else {
            val newId = UUID.randomUUID()
            insert { row ->
              row[id] = newId
              row[this.fullName] = fullName.maxLength(128)
              row[this.email] = address.maxLength(128)
              row[this.pictureUrl] = pictureUrl
              row[this.role] = role.name.lowercase()
              row[lastPathName] = ROOT
              row[lastSignIn] = now
            }
            logger.info { "Created user $address $newId" }
            newId
          }
        }
      }
    }

    fun emailForUuid(uuid: UUID): String? =
      transaction {
        with(UsersTable) {
          select(email).where { id eq uuid }.map { it[email] }.firstOrNull()
        }
      }

    /**
     * The address and session version of [uuid], or null when there is no such user. Session validation runs this on
     * every request that carries a session, so the two are read together, in one query.
     */
    fun sessionUserFor(uuid: UUID): SessionUser? =
      transaction {
        with(UsersTable) {
          select(email, sessionVersion)
            .where { id eq uuid }
            .map { SessionUser(it[email], it[sessionVersion]) }
            .firstOrNull()
        }
      }

    /**
     * Moves [uuid]'s session version on, which ends every session the user holds, in any browser. The increment is
     * done in SQL, so two sign-outs at once both count.
     */
    fun incrementSessionVersion(uuid: UUID) {
      transaction {
        with(UsersTable) {
          update({ id eq uuid }) { row -> row[sessionVersion] = sessionVersion + 1 }
        }
      }
    }

    /**
     * Brings each user's stored role into line with the role rule ([roleFor]) under [teacherEmails], and returns how
     * many it changed. The progress views filter on the stored role, which sign-in writes, while everything else
     * derives the role from `TEACHER_EMAILS`. Without this, an address on both sides of the rule (on the teacher list
     * and starting with two digits) would be counted by the role it had at its last sign-in: a student given teacher
     * access for a trial would have their test walk counted as a student's, until their next sign-in dropped their
     * earlier moves from the class pages. The list is read once a process, so syncing at startup keeps the two in step.
     * An address the rule no longer admits keeps its stored role: a teacher taken off the list stays out of the class
     * pages, as before.
     */
    fun syncStoredRoles(teacherEmails: Set<String> = SchoolConfig.teacherEmails): Int =
      transaction {
        with(UsersTable) {
          selectAll()
            .mapNotNull { row ->
              val now = roleFor(row[email], teacherEmails)?.name?.lowercase()
              if (now != null && now != row[role]) row[id].value to now else null
            }.onEach { (userId, now) -> update({ id eq userId }) { row -> row[role] = now } }
            .size
        }
      }

    /** The role is derived from [teacherEmails] now, as the route guards do, not the one stored at last sign-in. */
    fun userInfoFor(
      uuid: UUID,
      teacherEmails: Set<String> = SchoolConfig.teacherEmails,
    ): UserInfo? =
      transaction {
        with(UsersTable) {
          selectAll()
            .where { id eq uuid }
            .map {
              UserInfo(
              it[fullName],
              it[email],
              it[pictureUrl],
              roleFor(it[email], teacherEmails) ?: Role.STUDENT,
            )
            }.firstOrNull()
        }
      }

    fun findCurrentSlideForUser(
      uuid: String,
      slideDeck: SlideDeck,
    ): Slide =
      transaction {
        with(UsersTable) {
          select(lastPathName)
            .where { id eq uuid.toUuid() }
            .map { it[lastPathName] }
            .firstOrNull() ?: error("Missing uuid: $uuid")
        }.let { pathName -> slideDeck.findSlideByPathName(pathName) }
      }
  }
}

// The value a setting's masked log line shows when it is not set (EnvVar.maskFunc, Property.maskFunc)
const val UNASSIGNED = "unassigned"
