package com.github.pambrose

import com.github.pambrose.Email.Companion.EMPTY_EMAIL
import com.github.pambrose.Email.Companion.UNKNOWN_EMAIL
import com.github.pambrose.FullName.Companion.EMPTY_FULLNAME
import com.github.pambrose.common.util.*
import com.github.pambrose.dbms.UsersTable
import com.pambrose.common.exposed.get
import io.ktor.http.*
import mu.KLogging
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.measureTime

class User {

  constructor(uuid: UUID, initFields: Boolean) {
    this.uuid = uuid

    if (initFields) {
      measureTime {
        transaction {
          UsersTable
            .select { UsersTable.uuidCol eq this@User.uuid }
            .map { assignRowVals(it) }
            .firstOrNull() ?: error("UserId not found: ${this@User.uuid}")
        }
      }.also { logger.debug { "Selected user info in $it" } }
    }
  }

  private constructor(
    uuid: UUID,
    browserSession: BrowserSession?,
    row: ResultRow
  ) {
    this.uuid = uuid
    //this.browserSession = browserSession
    assignRowVals(row)
  }

  val uuid: UUID

  //val browserSession: BrowserSession?
  var userDbmsId: Long = -1
  var email: Email = EMPTY_EMAIL
  var fullName: FullName = EMPTY_FULLNAME
  private var saltBacking: String = ""
  private var digestBacking: String = ""

  val salt: String
    get() = saltBacking.ifBlank { throw IllegalArgumentException("Missing salt field") }
  val digest: String
    get() = digestBacking.ifBlank { throw IllegalArgumentException("Missing digest field") }

  private fun assignRowVals(row: ResultRow) {
    userDbmsId = row[UsersTable.id].value
    email = Email(row[UsersTable.email])
    fullName = FullName(row[UsersTable.fullName])
    saltBacking = row[UsersTable.salt]
    digestBacking = row[UsersTable.digest]
  }

  fun isInDbms() =
    transaction {
      val id = UsersTable.id
      UsersTable
        .slice(Count(id))
        .select { id eq userDbmsId }
        .map { it.get(0) as Long }
        .first() > 0
    }

  fun assignDigest(newDigest: String) =
    transaction {
      //PasswordResetsTable.deleteWhere { PasswordResetsTable.userRef eq userDbmsId }

      UsersTable
        .update({ UsersTable.id eq userDbmsId }) { row ->
          row[updated] = DateTime.now(DateTimeZone.UTC)
          row[digest] = newDigest
          digestBacking = newDigest
        }
    }

  fun deleteUser() {
    logger.info { "Deleting User: $uuid $fullName" }
    logger.info { "User Email: $email" }
    logger.info { "uuid: $uuid" }

    transaction {
      UsersTable.deleteWhere { UsersTable.id eq userDbmsId }
    }
  }

  override fun toString() = "User(uuid='$uuid', name='$fullName')"

  companion object : KLogging() {

    val userIdCache = ConcurrentHashMap<UUID, Long>()
    val emailCache = ConcurrentHashMap<UUID, Email>()

    fun UUID.toUser(browserSession: BrowserSession? = null) = User(this, true)

    fun UUID.toUser(row: ResultRow) = User(this, null, row)

    fun userExists(uuid: UUID) =
      transaction {
        UsersTable
          .slice(Count(UsersTable.id))
          .select { UsersTable.uuidCol eq uuid }
          .map { it[0] as Long }
          .first() > 0
      }

    fun fetchUserDbmsIdFromCache(uuid: UUID) =
      userIdCache.computeIfAbsent(uuid) {
        queryUserDbmsId(uuid).also { logger.debug { "Looked up userDbmsId for $uuid: $it" } }
      }

    fun fetchEmailFromCache(uuid: UUID) =
      emailCache.computeIfAbsent(uuid) {
        queryUserEmail(uuid).also { logger.debug { "Looked up email for $uuid: $it" } }
      }

    private fun queryUserDbmsId(uuid: UUID, defaultIfMissing: Long = -1) =
      transaction {
        UsersTable
          .slice(UsersTable.id)
          .select { UsersTable.uuidCol eq uuid }
          .map { it[UsersTable.id].value }
          .firstOrNull() ?: defaultIfMissing
      }

    private fun queryUserEmail(uuid: UUID, defaultIfMissing: Email = UNKNOWN_EMAIL) =
      transaction {
        UsersTable
          .slice(UsersTable.email)
          .select { UsersTable.uuidCol eq uuid }
          .map { Email(it[0] as String) }
          .firstOrNull() ?: defaultIfMissing
      }

    fun createUser(
      name: FullName,
      email: Email,
      password: Password,
      browserSession: BrowserSession?
    ): User =
      User(UUID.randomUUID(), false)
        .also { user ->
          transaction {
            val salt = newStringSalt()
            val digest = password.sha256(salt)
            val userDbmsId =
              UsersTable
                .insertAndGetId { row ->
                  row[uuidCol] = user.uuid
                  row[fullName] = name.value.maxLength(128)
                  row[UsersTable.email] = email.value.maxLength(128)
                  row[UsersTable.salt] = salt
                  row[UsersTable.digest] = digest
                }.value
          }
          logger.info { "Created user $email ${user.uuid}" }
        }

    private fun isRegisteredEmail(email: Email) = queryUserByEmail(email).isNotNull()

    fun isNotRegisteredEmail(email: Email) = !isRegisteredEmail(email)

    fun queryUserByEmail(email: Email): User? =
      transaction {
        UsersTable
          .slice(UsersTable.uuidCol)
          .select { UsersTable.email eq email.value }
          .map { (it[0] as UUID).toUser() }
          .firstOrNull()
          .also { logger.info { "queryUserByEmail() returned: ${it?.email ?: " ${email.value} not found"}" } }
      }

    fun queryUserByUuid(uuid: UUID): User? =
      transaction {
        UsersTable
          .slice(UsersTable.uuidCol)
          .select { UsersTable.uuidCol eq uuid }
          .map { (it[0] as UUID).toUser() }
          .firstOrNull()
          .also { logger.info { "queryUserByUuid() returned: ${it?.email ?: " $uuid not found"}" } }
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