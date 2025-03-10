package com.github.pambrose

import com.github.pambrose.Email.Companion.EMPTY_EMAIL
import com.github.pambrose.Email.Companion.UNKNOWN_EMAIL
import com.github.pambrose.FullName.Companion.EMPTY_FULLNAME
import com.github.pambrose.Utils.toUuid
import com.github.pambrose.common.util.*
import com.github.pambrose.slides.Slide
import com.github.pambrose.slides.SlideDeck
import com.github.pambrose.slides.SlideDeck.Companion.ROOT
import com.pambrose.common.exposed.get
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*
import kotlin.time.measureTime

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
  private var saltBacking: String = ""
  private var digestBacking: String = ""

  val salt: String
    get() = saltBacking.ifBlank { throw IllegalArgumentException("Missing salt field") }
  val digest: String
    get() = digestBacking.ifBlank { throw IllegalArgumentException("Missing digest field") }

  private fun assignRowVals(row: ResultRow) {
    userUuid = row[UsersTable.id].toString()
    email = Email(row[UsersTable.email])
    fullName = FullName(row[UsersTable.fullName])
    saltBacking = row[UsersTable.salt]
    digestBacking = row[UsersTable.digest]
  }

  fun isInDbms() =
    transaction {
      val id = UsersTable.id
      with(UsersTable) {
        select(Count(id))
          .where { id eq userUuid.toUuid() }
          .map { it[0] as Long }
          .first() > 0
      }
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

    private fun queryUserDbmsId(
      uuid: UUID,
      defaultIfMissing: Long = -1,
    ) {
      transaction {
        with(UsersTable) {
          select(id)
            .where { id eq uuid }
            .map { it[id].value }
            .firstOrNull() ?: defaultIfMissing
        }
      }
    }

    private fun queryUserEmail(
      uuid: UUID,
      defaultIfMissing: Email = UNKNOWN_EMAIL,
    ): Email =
      transaction {
        with(UsersTable) {
          select(email)
            .where { id eq uuid }
            .map { Email(it[0] as String) }
            .firstOrNull() ?: defaultIfMissing
        }
      }

    fun createUser(
      name: FullName,
      emailVal: Email,
      password: Password,
    ): User =
      User(
        UUID.randomUUID(),
        false,
      )
        .also { user ->
          transaction {
            val saltVal = newStringSalt()
            val digestVal = password.sha256(saltVal)
            with(UsersTable) {
              insert { row ->
                row[id] = user.uuid
                row[fullName] = name.value.maxLength(128)
                row[email] = emailVal.value.maxLength(128)
                row[salt] = saltVal
                row[digest] = digestVal
                row[lastPathName] = ROOT
              }
            }
          }
          logger.info { "Created user $emailVal ${user.uuid}" }
        }

    private fun isRegisteredEmail(email: Email) = queryUserByEmail(email).isNotNull()

    fun isNotRegisteredEmail(email: Email) = !isRegisteredEmail(email)

    fun isValidUuid(uuid: String) =
      transaction {
        with(UsersTable) {
          select(Count(id))
            .where { id eq uuid.toUuid() }
            .map { it[0] as Long }
            .first() > 0
        }
      }

    fun queryUserByEmail(emailVal: Email): User? =
      transaction {
        with(UsersTable) {
          select(id)
            .where { email eq emailVal.value }
            .map { row -> row[id].toUuid().toUser() }
            .firstOrNull()
            .also { logger.info { "queryUserByEmail() returned: ${it?.email ?: " ${emailVal.value} not found"}" } }
        }
      }

    fun queryUserByUuid(uuid: UUID): User? =
      transaction {
        with(UsersTable) {
          select(id)
            .where { id eq uuid }
            .map { row -> row[id].toUuid().toUser() }
            .firstOrNull()
            .also { logger.info { "queryUserByUuid() returned: ${it?.email ?: " $uuid not found"}" } }
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

    fun Parameters.getFullName(name: String) = this[name]?.let { FullName(it) } ?: EMPTY_FULLNAME
  }
}

@JvmInline
value class Password(val value: String) {
  val length get() = value.length

  fun isBlank() = value.isBlank()

  fun sha256(salt: String) = value.sha256(salt)

  override fun toString() = value

  companion object {
    private val EMPTY_PASSWORD = Password("")

    fun Parameters.getPassword(name: String) = this[name]?.let { Password(it) } ?: EMPTY_PASSWORD
  }
}

@JvmInline
value class Email(val value: String) {
  fun isBlank() = value.isBlank()

  fun isNotBlank() = value.isNotBlank()

  fun isNotValidEmail() = value.isNotValidEmail()

  override fun toString() = value

  companion object {
    val EMPTY_EMAIL = Email("")
    val UNKNOWN_EMAIL = Email(UNKNOWN)

    fun Parameters.getEmail(name: String) = this[name]?.let { Email(it) } ?: EMPTY_EMAIL
  }
}

const val UNKNOWN = "Unknown"
const val UNASSIGNED = "unassigned"
