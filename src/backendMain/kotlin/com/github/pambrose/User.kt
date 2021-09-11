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
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.measureTime

class User {

  constructor(
    userId: String,
    //browserSession: BrowserSession?,
    initFields: Boolean
  ) {
    this.userId = userId
    //this.browserSession = browserSession

    if (initFields) {
      measureTime {
        transaction {
          UsersTable
            .select { UsersTable.userId eq this@User.userId }
            .map { assignRowVals(it) }
            .firstOrNull() ?: error("UserId not found: ${this@User.userId}")
        }
      }.also { logger.debug { "Selected user info in $it" } }
    }
  }

  private constructor(
    userId: String,
    browserSession: BrowserSession?,
    row: ResultRow
  ) {
    this.userId = userId
    //this.browserSession = browserSession
    assignRowVals(row)
  }

  val userId: String

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
    logger.info { "Deleting User: $userId $fullName" }
    logger.info { "User Email: $email" }
    logger.info { "UserId: $userId" }

    transaction {
      UsersTable.deleteWhere { UsersTable.id eq userDbmsId }
    }
  }

  override fun toString() = "User(userId='$userId', name='$fullName')"

  companion object : KLogging() {

    val userIdCache = ConcurrentHashMap<String, Long>()
    val emailCache = ConcurrentHashMap<String, Email>()

    fun String.toUser(browserSession: BrowserSession? = null) = User(this, true)

    fun String.toUser(row: ResultRow) = User(this, null, row)

    fun userExists(userId: String) =
      transaction {
        UsersTable
          .slice(Count(UsersTable.id))
          .select { UsersTable.userId eq userId }
          .map { it[0] as Long }
          .first() > 0
      }

    fun fetchUserDbmsIdFromCache(userId: String) =
      userIdCache.computeIfAbsent(userId) {
        queryUserDbmsId(userId).also { logger.debug { "Looked up userDbmsId for $userId: $it" } }
      }

    fun fetchEmailFromCache(userId: String) =
      emailCache.computeIfAbsent(userId) {
        queryUserEmail(userId).also { logger.debug { "Looked up email for $userId: $it" } }
      }

    private fun queryUserDbmsId(userId: String, defaultIfMissing: Long = -1) =
      transaction {
        UsersTable
          .slice(UsersTable.id)
          .select { UsersTable.userId eq userId }
          .map { it[UsersTable.id].value }
          .firstOrNull() ?: defaultIfMissing
      }

    private fun queryUserEmail(userId: String, defaultIfMissing: Email = UNKNOWN_EMAIL) =
      transaction {
        UsersTable
          .slice(UsersTable.email)
          .select { UsersTable.userId eq userId }
          .map { Email(it[0] as String) }
          .firstOrNull() ?: defaultIfMissing
      }

    fun createUser(
      name: FullName,
      email: Email,
      password: Password,
      browserSession: BrowserSession?
    ): User =
      User(randomId(25), false)
        .also { user ->
          transaction {
            val salt = newStringSalt()
            val digest = password.sha256(salt)
            val userDbmsId =
              UsersTable
                .insertAndGetId { row ->
                  row[userId] = user.userId
                  row[fullName] = name.value.maxLength(128)
                  row[UsersTable.email] = email.value.maxLength(128)
                  row[UsersTable.salt] = salt
                  row[UsersTable.digest] = digest
                }.value
          }
          logger.info { "Created user $email ${user.userId}" }
        }

    private fun isRegisteredEmail(email: Email) = queryUserByEmail(email).isNotNull()

    fun isNotRegisteredEmail(email: Email) = !isRegisteredEmail(email)

    fun queryUserByEmail(email: Email): User? =
      transaction {
        UsersTable
          .slice(UsersTable.userId)
          .select { UsersTable.email eq email.value }
          .map { (it[0] as String).toUser() }
          .firstOrNull()
          .also { logger.info { "lookupUserByEmail() returned: ${it?.email ?: " ${email.value} not found"}" } }
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