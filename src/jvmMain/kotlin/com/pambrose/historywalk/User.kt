package com.pambrose.historywalk

import com.pambrose.historywalk.Email.Companion.EMPTY_EMAIL
import com.pambrose.historywalk.FullName.Companion.EMPTY_FULLNAME
import com.pambrose.historywalk.Utils.toUuid
import com.pambrose.historywalk.auth.Roles.roleFor
import com.pambrose.historywalk.auth.SchoolConfig
import com.pambrose.historywalk.dbms.UsersTable
import com.pambrose.historywalk.slides.Slide
import com.pambrose.historywalk.slides.SlideDeck
import com.pambrose.historywalk.slides.SlideDeck.Companion.ROOT
import com.pambrose.common.exposed.get
import com.pambrose.common.util.*
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.*
import kotlin.time.measureTime
import kotlinx.datetime.toKotlinLocalDateTime

class User {
  constructor(uuid: UUID, initFields: Boolean) {
    this.uuid = uuid

    if (initFields) {
      measureTime {
        transaction {
          with(UsersTable) {
            selectAll()
              .where { id eq uuid }
              .map { assignRowVals(it) }
              .firstOrNull() ?: error("UserId not found: ${this@User.uuid}")
          }
        }
      }.also { logger.debug { "Selected user info in $it" } }
    }
  }

  private constructor(
    uuid: UUID,
    row: ResultRow,
  ) {
    this.uuid = uuid
    // this.browserSession = browserSession
    assignRowVals(row)
  }

  val uuid: UUID

  // val browserSession: BrowserSession?
  var userUuid: String = "-1"
  var email: Email = EMPTY_EMAIL
  var fullName: FullName = EMPTY_FULLNAME
  private fun assignRowVals(row: ResultRow) {
    userUuid = row[UsersTable.id].toString()
    email = Email(row[UsersTable.email])
    fullName = FullName(row[UsersTable.fullName])
  }

  fun deleteUser() {
    logger.info { "Deleting User: $uuid $fullName" }
    logger.info { "User Email: $email" }
    logger.info { "uuid: $uuid" }

    transaction {
      with(UsersTable) {
        deleteWhere { id eq userUuid.toUuid() }
      }
    }
  }

  override fun toString() = "User(uuid='$uuid', name='$fullName')"

  companion object {
    private val logger = KotlinLogging.logger {}

    fun UUID.toUser(): User =
      User(
        this,
        true,
      )

    fun UUID.toUser(row: ResultRow): User =
      User(
        uuid = this,
        row = row,
      )

    fun userExists(uuid: UUID) =
      transaction {
        with(UsersTable) {
          select(Count(id))
            .where { id eq uuid }
            .map { it[0] as Long }
            .first() > 0
        }
      }

    fun upsertFromGoogle(
      email: String,
      fullName: String,
      pictureUrl: String?,
      role: Role,
    ): UUID {
      val address = email.lowercase()
      val now = java.time.LocalDateTime.now().toKotlinLocalDateTime()
      return transaction {
        with(UsersTable) {
          val existing = select(id).where { this@with.email eq address }.map { it[id].value }.firstOrNull()
          if (existing != null) {
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

    /** The role is derived from [teacherEmails] now, as the route guards do, not the one stored at last sign-in. */
    fun userInfoFor(
      uuid: UUID,
      teacherEmails: Set<String> = SchoolConfig.teacherEmails,
    ): UserInfo? =
      transaction {
        with(UsersTable) {
          selectAll()
            .where { id eq uuid }
            .map { UserInfo(it[fullName], it[email], it[pictureUrl], roleFor(it[email], teacherEmails) ?: Role.STUDENT) }
            .firstOrNull()
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

@JvmInline
value class FullName(val value: String) {
  fun isBlank() = value.isBlank()

  override fun toString() = value

  companion object {
    val EMPTY_FULLNAME = FullName("")
  }
}

@JvmInline
value class Email(val value: String) {
  fun isBlank() = value.isBlank()

  fun isNotBlank() = value.isNotBlank()

  override fun toString() = value

  companion object {
    val EMPTY_EMAIL = Email("")
  }
}

const val UNKNOWN = "Unknown"
const val UNASSIGNED = "unassigned"
