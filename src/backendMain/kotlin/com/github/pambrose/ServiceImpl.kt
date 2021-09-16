package com.github.pambrose

import com.github.pambrose.Slide.Companion.findSlide
import com.github.pambrose.common.util.newStringSalt
import com.github.pambrose.common.util.sha256
import com.github.pambrose.dbms.UserChoiceTable
import com.github.pambrose.dbms.UsersTable
import com.google.inject.Inject
import io.ktor.application.*
import io.ktor.sessions.*
import mu.KLogging
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.*

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

  override suspend fun currentSlide(title: String): SlideData {

    logger.info { "profile=${call.sessions.get<Profile>()}" }
    logger.info { "title=$title" }

    val uuid = call.profile?.uuid ?: error("Missing profile")

    val slide = findSlide(UUID.fromString(uuid))

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
    slides += slide.title

    return SlideData(slide.title, content, choices, orientation, parentTitles, slides.size)
      .also { logger.info { "Returning: $it \n" } }
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

  fun updateLastTitle(uuid: String, title: String) {
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

    val sessionChoices = mutableMapOf<String, MutableSet<String>>()
  }
}
