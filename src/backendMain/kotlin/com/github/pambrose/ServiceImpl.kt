package com.github.pambrose

import com.github.andrewoma.kwery.core.builder.query
import com.github.pambrose.Db.dbQuery
import com.github.pambrose.Db.queryList
import com.github.pambrose.Slide.Companion.findSlide
import com.github.pambrose.common.util.isNull
import com.github.pambrose.common.util.randomId
import com.google.inject.Inject
import io.ktor.application.*
import io.ktor.sessions.*
import org.apache.commons.codec.digest.DigestUtils
import org.jetbrains.exposed.sql.*
import org.joda.time.DateTime
import java.sql.ResultSet
import java.time.ZoneId

suspend fun <RESP> ApplicationCall.withProfile(block: suspend (Profile) -> RESP): RESP {
  val profile = this.sessions.get<Profile>()
  return profile?.let {
    block(profile)
  } ?: throw IllegalStateException("Profile not set!")
}

actual class AddressService : IAddressService {

  @Inject
  lateinit var call: ApplicationCall

  override suspend fun getAddressList(search: String?, types: String, sort: Sort) =
    call.withProfile { profile ->
      dbQuery {
        val query = query {
          select("SELECT * FROM address")
          whereGroup {
            where("user_id = :user_id")
            parameter("user_id", profile.id)
            search?.let {
              where(
                """(lower(first_name) like :search
                            OR lower(last_name) like :search
                            OR lower(email) like :search
                            OR lower(phone) like :search
                            OR lower(postal_address) like :search)""".trimMargin()
              )
              parameter("search", "%${it.lowercase()}%")
            }
            if (types == "fav") {
              where("favourite")
            }
          }
          when (sort) {
            Sort.FN -> orderBy("lower(first_name)")
            Sort.LN -> orderBy("lower(last_name)")
            Sort.E -> orderBy("lower(email)")
            Sort.F -> orderBy("favourite")
          }
        }
        queryList(query.sql, query.parameters) {
          toAddress(it)
        }
      }
    }

  override suspend fun addAddress(address: Address) = call.withProfile { profile ->
    val key = dbQuery {
      (AddressDao.insert {
        it[firstName] = address.firstName
        it[lastName] = address.lastName
        it[email] = address.email
        it[phone] = address.phone
        it[postalAddress] = address.postalAddress
        it[favourite] = address.favourite ?: false
        it[createdAt] = DateTime()
        it[userId] = profile.id!!

      } get AddressDao.id)
    }
    getAddress(key)!!
  }

  override suspend fun updateAddress(address: Address) = call.withProfile { profile ->
    address.id?.let {
      getAddress(it)?.let { oldAddress ->
        dbQuery {
          AddressDao.update({ AddressDao.id eq it }) {
            it[firstName] = address.firstName
            it[lastName] = address.lastName
            it[email] = address.email
            it[phone] = address.phone
            it[postalAddress] = address.postalAddress
            it[favourite] = address.favourite ?: false
            it[createdAt] = oldAddress.createdAt
              ?.let { DateTime(java.util.Date.from(it.atZone(ZoneId.systemDefault()).toInstant())) }
            it[userId] = profile.id!!
          }
        }
      }
      getAddress(it)
    } ?: throw IllegalArgumentException("The ID of the address not set")
  }

  override suspend fun deleteAddress(id: Int): Boolean = call.withProfile { profile ->
    dbQuery {
      AddressDao.deleteWhere { (AddressDao.userId eq profile.id!!) and (AddressDao.id eq id) } > 0
    }
  }

  private suspend fun getAddress(id: Int): Address? = dbQuery {
    AddressDao.select {
      AddressDao.id eq id
    }.mapNotNull { toAddress(it) }.singleOrNull()
  }

  private fun toAddress(row: ResultRow): Address =
    Address(
      id = row[AddressDao.id],
      firstName = row[AddressDao.firstName],
      lastName = row[AddressDao.lastName],
      email = row[AddressDao.email],
      phone = row[AddressDao.phone],
      postalAddress = row[AddressDao.postalAddress],
      favourite = row[AddressDao.favourite],
      createdAt = row[AddressDao.createdAt]?.millis?.let { java.util.Date(it) }?.toInstant()
        ?.atZone(ZoneId.systemDefault())?.toLocalDateTime(),
      userId = row[AddressDao.userId]
    )

  private fun toAddress(rs: ResultSet): Address =
    Address(
      id = rs.getInt(AddressDao.id.name),
      firstName = rs.getString(AddressDao.firstName.name),
      lastName = rs.getString(AddressDao.lastName.name),
      email = rs.getString(AddressDao.email.name),
      phone = rs.getString(AddressDao.phone.name),
      postalAddress = rs.getString(AddressDao.postalAddress.name),
      favourite = rs.getBoolean(AddressDao.favourite.name),
      createdAt = rs.getTimestamp(AddressDao.createdAt.name)?.toInstant()
        ?.atZone(ZoneId.systemDefault())?.toLocalDateTime(),
      userId = rs.getInt(AddressDao.userId.name)
    )
}

actual class ProfileService : IProfileService {

  @Inject
  lateinit var call: ApplicationCall

  override suspend fun getProfile() = call.withProfile { it }

}

actual class RegisterProfileService : IRegisterProfileService {

  override suspend fun registerProfile(profile: Profile, password: String): Boolean {
    try {
      dbQuery {
        UserDao.insert {
          it[this.name] = profile.name!!
          it[this.username] = profile.username!!
          it[this.password] = DigestUtils.sha256Hex(password)
        }
      }
    } catch (e: Exception) {
      throw Exception("Register operation failed!")
    }
    return true
  }
}

actual class ContentService : IContentService {
  @Inject
  lateinit var call: ApplicationCall

  override suspend fun currentSlide(title: String): SlideData {

    println("name= ${call.sessions.get<Profile>()}")
    println("session= ${call.browserSession}")

    val sessionId = call.browserSession?.id ?: error("Missing browser session")

    val slide = findSlide(title)

    val titleVal = slide.title

    val escaped = slide.content
      .replace("<", ltEscape)
      .replace(">", gtEscape)

    val content =
      MarkdownParser.toHtml(escaped)
        .replace(ltEscape, "<")
        .replace(gtEscape, ">")

    val choices = slide.choices.map { (choice, destination) -> ChoiceTitle(choice, destination) }

    val orientation = slide.choiceOrientation

    val parentTitles =
      mutableListOf<String>()
        .also { parentTitles ->
          var currSlide = slide.parentSlide
          while (currSlide != null) {
            parentTitles += currSlide.title
            currSlide = currSlide.parentSlide
          }
        }
        .reversed()

    val slides = sessionChoices.computeIfAbsent(sessionId) { mutableSetOf() }
    slides += titleVal

    return SlideData(titleVal, content, choices, orientation, parentTitles, slides.size)
  }

  override suspend fun choose(fromTitle: String, choice: String, choiceTitle: String): ChoiceReason {
    val user = call.sessions.get<Profile>()?.name ?: error("Missing profile")
    //println("User: '$user' from: '$fromTitle' to: '$toTitle' choice: '$choice' reason: '$reason'")
    val userMoves = users.computeIfAbsent(user) { mutableListOf() }
    val userChoice = userMoves.firstOrNull { it.fromTitle == fromTitle && it.choice == choice }

    return if (userChoice.isNull()) {
      Choice(fromTitle, choice, choiceTitle).let { newChoice ->
        userMoves.add(newChoice)
        ChoiceReason(newChoice.choiceId, newChoice.reason)
      }
    } else {
      ChoiceReason(userChoice.choiceId, userChoice.reason)
    }.also {
      println(users[user])
    }
  }

  override suspend fun reason(choiceId: String, reason: String): String {
    val user = call.sessions.get<Profile>()?.name ?: error("Missing profile")
    val userMoves = users[user] ?: error("Missing user: $user")
    val userChoice = userMoves.firstOrNull { it.choiceId == choiceId } ?: error("Missing choiceId: $choiceId")
    println("Assigning $userChoice the reason: $reason")
    userChoice.reason = reason
    return ""
  }

  companion object {
    const val ltEscape = "---LT---"
    const val gtEscape = "---GT---"

    val users = mutableMapOf<String, MutableList<Choice>>()
    val sessionChoices = mutableMapOf<String, MutableSet<String>>()
  }
}

data class Choice(val fromTitle: String, val choice: String, val choiceTitle: String, var reason: String = "") {
  val choiceId = randomId(10)
}