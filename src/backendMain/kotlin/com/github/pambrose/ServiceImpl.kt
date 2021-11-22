package com.github.pambrose

import com.github.pambrose.User.Companion.findSlide
import com.github.pambrose.dbms.UserChoiceTable
import com.github.pambrose.dbms.UsersTable
import com.github.pambrose.slides.Slide
import com.google.inject.Inject
import com.pambrose.common.exposed.get
import io.ktor.application.*
import mu.KLogging
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

actual class RegisterUserService : IRegisterUserService {

  override suspend fun registerUser(registerData: RegisterData): Boolean {
    try {
      User.createUser(FullName(registerData.fullName), Email(registerData.email), Password(registerData.password))
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

  override suspend fun getCurrentSlide(): SlideData {
    logger.debug { "userId=${call.userId}" }
    val uuid = call.userId.uuid
    val slide = findSlide(uuid, HistoryWalkServer.masterSlides.get())
    return slideData(uuid, slide)
  }

  override suspend fun makeChoice(fromTitle: String, abbrev: String, title: String): UserChoice =
    transaction {
      // See if user has an entry for that transition
      val uuid = call.userId.uuid
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

  override suspend fun provideReason(fromTitle: String, abbrev: String, title: String, reason: String) =
    transaction {
      val uuid = call.userId.uuid
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

      val slide = findSlide(uuid, HistoryWalkServer.masterSlides.get())
      slideData(uuid, slide)
    }

  override suspend fun goBackInTime(title: String) =
    transaction {
      val uuid = call.userId.uuid
      updateLastTitle(uuid, title)
      val slide = findSlide(uuid, HistoryWalkServer.masterSlides.get())
      slideData(uuid, slide)
    }

  companion object : KLogging() {
    private const val ltEscape = "---LT---"
    private const val gtEscape = "---GT---"

    fun updateLastTitle(uuid: String, title: String) {
      UsersTable
        .update({ UsersTable.uuidCol eq UUID.fromString(uuid) }) { row ->
          row[UsersTable.lastTitle] = title
        }.also { count ->
          if (count != 1)
            error("Missing uuid: $uuid")
        }
    }

    fun deleteChoices(uuid: String) {
      UserChoiceTable
        .deleteWhere { UserChoiceTable.userUuid eq UUID.fromString(uuid) }.also { count ->
          logger.info("Deleted $count records for uuid: $uuid")
        }
    }

    private fun String.transformText(): String {
      val escaped = this
        .replace("<", ltEscape)
        .replace(">", gtEscape)

      return MarkdownParser.toHtml(escaped)
        .replace(ltEscape, "<")
        .replace(gtEscape, ">")
    }

    private fun slideData(uuid: String, slide: Slide): SlideData {

      val content = slide.content.transformText()

      val choices = slide.choices.map { (choice, destination) -> ChoiceTitle(choice, destination) }

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

      val count = slideCount(uuid)

      return SlideData(slide.title, content, slide.success, choices, slide.verticalChoices, parentTitles, count)
    }

    private fun slideCount(uuid: String) =
      transaction {
        UserChoiceTable
          .slice(Count(UserChoiceTable.id))
          .select { UserChoiceTable.userUuid eq UUID.fromString(uuid) }
          .map { it[0] as Long }
          .first()
      }
  }
}
