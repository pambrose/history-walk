package com.github.pambrose

import com.github.pambrose.User.Companion.findSlideForUser
import com.github.pambrose.slides.ImageElement
import com.github.pambrose.slides.Slide
import com.github.pambrose.slides.TextElement
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
    val slide = findSlideForUser(uuid, HistoryWalkServer.masterSlides.get())
    return slideData(uuid, slide)
  }

  override suspend fun makeChoice(fromTitle: String, abbrev: String, toTitle: String, advance: Boolean): UserChoice =
    transaction {
      // See if user has an entry for that transition
      val uuid = call.userId.uuid
      (UserChoiceTable
        .slice(UserChoiceTable.fromTitle, UserChoiceTable.abbrev, UserChoiceTable.title, UserChoiceTable.reason)
        .select { (UserChoiceTable.userUuid eq UUID.fromString(uuid)) and (UserChoiceTable.fromTitle eq fromTitle) and (UserChoiceTable.title eq toTitle) }
        .map { row ->
          UserChoice(
            row[UserChoiceTable.fromTitle],
            row[UserChoiceTable.abbrev],
            row[UserChoiceTable.title],
            row[UserChoiceTable.reason],
          )
        }
        .firstOrNull() ?: UserChoice(fromTitle, abbrev, toTitle, if (advance) "Advance" else "")
          )
        .also { userChoice ->
          if (userChoice.reason.isNotBlank()) {
            updateLastTitle(uuid, toTitle)
          }
        }
    }

  override suspend fun provideReason(fromTitle: String, abbrev: String, toTitle: String, reason: String) =
    transaction {
      val uuid = call.userId.uuid
      UserChoiceTable
        .insertAndGetId { row ->
          row[UserChoiceTable.uuidCol] = UUID.randomUUID()
          row[UserChoiceTable.userUuid] = UUID.fromString(uuid)
          row[UserChoiceTable.fromTitle] = fromTitle
          row[UserChoiceTable.abbrev] = abbrev
          row[UserChoiceTable.title] = toTitle
          row[UserChoiceTable.reason] = reason
        }.value

      updateLastTitle(uuid, toTitle)

      val slide = findSlideForUser(uuid, HistoryWalkServer.masterSlides.get())
      slideData(uuid, slide)
    }

  override suspend fun goBackInTime(title: String) =
    transaction {
      val uuid = call.userId.uuid
      updateLastTitle(uuid, title)
      val slide = findSlideForUser(uuid, HistoryWalkServer.masterSlides.get())
      slideData(uuid, slide)
    }

  companion object : KLogging() {
    private const val ltEscape = "---LT---"
    private const val gtEscape = "---GT---"
    private const val dquoteEscape = "---DQ---"
    private const val squoteEscape = "---SQ---"

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
        .replace("\"", dquoteEscape)
        .replace("\"", squoteEscape)

      return MarkdownParser.toHtml(escaped)
        .replace(ltEscape, "<")
        .replace(gtEscape, ">")
        .replace(dquoteEscape, "'")
        .replace(squoteEscape, "\"")
        .also {
          // println("Post conversion:\n$it")
        }
    }

    private fun slideData(uuid: String, slide: Slide): SlideData {

      val content = mutableListOf<ElementData>()

      slide.content.forEach { element ->
        when (element) {
          is TextElement -> {
            val stripped = element.text
              .splitToSequence("\n")
              .map { line -> line.trim() }
              .joinToString("\n")
            content += ElementData(ElementType.TEXT, stripped.transformText())
          }
          is ImageElement -> content += ElementData(ElementType.IMAGE, element.src, element.width, element.height)
        }
      }

      val choices =
        slide.choices.map { (choice, destination) ->
          SlideChoice(choice, destination)
        }

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

      return slide.run {
        val count = slideCount(uuid)
        val showResetButon = EnvVar.SHOW_RESET_BUTTON.getEnv(false)
        SlideData(title, content, success, choices, verticalChoices, parentTitles, count, showResetButon)
      }
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
