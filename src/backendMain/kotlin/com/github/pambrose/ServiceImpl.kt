package com.github.pambrose

import com.github.pambrose.Slide.Companion.findSlide
import com.github.pambrose.dbms.UserChoiceTable
import com.github.pambrose.dbms.UsersTable
import com.google.inject.Inject
import com.pambrose.common.exposed.get
import io.ktor.application.*
import io.ktor.sessions.*
import mu.KLogging
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

actual class RegisterUserService : IRegisterUserService {

  override suspend fun registerUser(registerData: RegisterData): Boolean {
    try {
      User.createUser(FullName(registerData.name), Email(registerData.email), Password(registerData.password))
    } catch (e: Exception) {
      logger.error(e) { "Failed to register user" }
      return false
    }
    return true
  }

  companion object : KLogging()
}

actual class ContentService : IContentService {
  @Inject
  lateinit var call: ApplicationCall

  override suspend fun currentSlide(): SlideData {
    logger.info { "profile=${call.sessions.get<Profile>()}" }
    val uuid = call.profile?.uuid ?: error("Missing profile")
    val slide = findSlide(uuid)
    return slideData(uuid, slide)
  }

  override suspend fun choose(fromTitle: String, abbrev: String, title: String): UserChoice =
    transaction {
      // See if user has an entry for that transition
      val uuid = call.sessions.get<Profile>()?.uuid ?: error("Missing profile")
      (UserChoiceTable
        .slice(UserChoiceTable.fromTitle, UserChoiceTable.abbrev, UserChoiceTable.title, UserChoiceTable.reason)
        .select { (UserChoiceTable.userUuid eq UUID.fromString(uuid)) and (UserChoiceTable.fromTitle eq fromTitle) and (UserChoiceTable.title eq title) }
        .map { row ->
          UserChoice(
            row[UserChoiceTable.fromTitle],
            row[UserChoiceTable.abbrev],
            row[UserChoiceTable.title],
            row[UserChoiceTable.reason],
          )
        }
        .firstOrNull() ?: UserChoice(fromTitle, title, abbrev, ""))
        .also { userChoice ->
          if (userChoice.reason.isNotBlank()) {
            updateLastTitle(uuid, title)
          }
        }
    }

  override suspend fun reason(fromTitle: String, abbrev: String, title: String, reason: String): String {
    transaction {
      val uuid = call.sessions.get<Profile>()?.uuid ?: error("Missing profile")
      UserChoiceTable
        .insertAndGetId { row ->
          row[UserChoiceTable.uuidCol] = UUID.randomUUID()
          row[UserChoiceTable.userUuid] = UUID.fromString(uuid)
          row[UserChoiceTable.fromTitle] = fromTitle
          row[UserChoiceTable.abbrev] = abbrev
          row[UserChoiceTable.title] = title
          row[UserChoiceTable.reason] = reason
        }.value

      updateLastTitle(uuid, title)
    }
    return ""
  }

  override suspend fun goBack(title: String): SlideData {
    val uuid = call.sessions.get<Profile>()?.uuid ?: error("Missing profile")
    transaction {
      updateLastTitle(uuid, title)
    }
    val slide = findSlide(uuid)
    return slideData(uuid, slide)
  }

  private fun updateLastTitle(uuid: String, title: String) {
    UsersTable
      .update({ UsersTable.uuidCol eq UUID.fromString(uuid) }) { row ->
        row[UsersTable.lastTitle] = title
      }.also { count ->
        if (count != 1)
          error("Missing uuid: $uuid")
      }
  }

  companion object : KLogging() {
    const val ltEscape = "---LT---"
    const val gtEscape = "---GT---"

    fun slideData(uuid: String, slide: Slide): SlideData {
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

      val count =
        transaction {
          UserChoiceTable
            .slice(Count(UserChoiceTable.id))
            .select { UserChoiceTable.userUuid eq UUID.fromString(uuid) }
            .map { it.get(0) as Long }
            .first()
            .toInt()
        }

      return SlideData(slide.title, content, choices, orientation, parentTitles, count)
        .also { logger.info { "Returning: $it \n" } }
    }
  }
}
