package com.github.pambrose

import com.github.pambrose.Slide.Companion.findSlide
import com.github.pambrose.common.util.isNull
import com.github.pambrose.common.util.newStringSalt
import com.github.pambrose.common.util.randomId
import com.github.pambrose.common.util.sha256
import com.github.pambrose.dbms.UsersTable
import com.google.inject.Inject
import io.ktor.application.*
import io.ktor.sessions.*
import mu.KLogging
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

suspend fun <RESP> ApplicationCall.withProfile(block: suspend (Profile) -> RESP): RESP {
  val profile = this.sessions.get<Profile>()
  return profile?.let {
    block(profile)
  } ?: throw IllegalStateException("Profile not set!")
}

actual class ProfileService : IProfileService {

  @Inject
  lateinit var call: ApplicationCall

  override suspend fun getProfile() = call.withProfile { it }
}

actual class RegisterProfileService : IRegisterProfileService {

  override suspend fun registerProfile(profile: Profile, password: String): Boolean {
    try {
      User(UUID.randomUUID(), false)
        .also { user ->
          transaction {
            val salt = newStringSalt()
            val digest = password.sha256(salt)
            val userDbmsId =
              UsersTable
                .insertAndGetId { row ->
                  row[UsersTable.uuidCol] = user.uuid
                  row[UsersTable.fullName] = profile.name
                  row[UsersTable.email] = profile.email
                  row[UsersTable.salt] = salt
                  row[UsersTable.digest] = digest
                }.value

//            val browserId =
//              browserSession?.sessionDbmsId() ?: error("Missing browser session")
//
          }
        }
    } catch (e: Exception) {
      e.printStackTrace()
      throw Exception("Register operation failed!")
    }
    return true
  }
}

actual class ContentService : IContentService {
  @Inject
  lateinit var call: ApplicationCall

  override suspend fun hello(): String {
    return "world"
  }

  override suspend fun currentSlide(title: String): SlideData {

    logger("In currentSlide() at ${System.currentTimeMillis()}")
    logger.info { "provile=${call.sessions.get<Profile>()}" }

    val uuid = call.profile?.uuid ?: error("Missing profile")

    logger.info { "title=$title" }
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

    val slides = sessionChoices.computeIfAbsent(uuid) { mutableSetOf() }
    slides += titleVal

    return SlideData(titleVal, content, choices, orientation, parentTitles, slides.size)
      .also { logger.info { "Returning: $it \n" } }
  }

  override suspend fun choose(fromTitle: String, choice: String, choiceTitle: String): ChoiceReason {
    val user = call.sessions.get<Profile>()?.name ?: error("Missing profile")
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
    logger.info { "Assigning $userChoice the reason: $reason" }
    userChoice.reason = reason
    return ""
  }

  companion object : KLogging() {
    const val ltEscape = "---LT---"
    const val gtEscape = "---GT---"

    val users = mutableMapOf<String, MutableList<Choice>>()
    val sessionChoices = mutableMapOf<String, MutableSet<String>>()
  }
}

data class Choice(val fromTitle: String, val choice: String, val choiceTitle: String, var reason: String = "") {
  val choiceId = randomId(10)
}