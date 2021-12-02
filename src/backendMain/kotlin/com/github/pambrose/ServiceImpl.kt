package com.github.pambrose

import com.github.pambrose.User.Companion.findSlideForUser
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
    val slide = findSlideForUser(uuid, HistoryWalkServer.masterSlides.get())
    return slideData(uuid, slide)
  }

  override suspend fun makeChoice(
    fromfqName: String,
    fromTitle: String,
    slideChoice: SlideChoice,
    advance: Boolean
  ): UserChoice =
    transaction {
      // See if user has an entry for that transition
      val uuid = call.userId.uuid
      (UserChoiceTable
        .slice(UserChoiceTable.fromTitle, UserChoiceTable.choiceText, UserChoiceTable.toTitle, UserChoiceTable.reason)
        .select { (UserChoiceTable.userUuid eq UUID.fromString(uuid)) and (UserChoiceTable.fromfqName eq fromfqName) and (UserChoiceTable.tofqName eq slideChoice.fqName) }
        .map { row ->
          UserChoice(
            row[UserChoiceTable.fromfqName],
            row[UserChoiceTable.fromTitle],
            SlideChoice(
              row[UserChoiceTable.choiceText],
              row[UserChoiceTable.tofqName],
              row[UserChoiceTable.toTitle]
            ),
            row[UserChoiceTable.reason],
          )
        }
        .firstOrNull() ?: UserChoice(fromfqName, fromTitle, slideChoice, if (advance) "Advance" else "")
          )
        .also { userChoice ->
          if (userChoice.reason.isNotBlank()) {
            updateLastSlide(uuid, slideChoice.fqName)
          }
        }
    }

  override suspend fun provideReason(fromfqName: String, fromTitle: String, slideChoice: SlideChoice, reason: String) =
    transaction {
      val uuid = call.userId.uuid
      UserChoiceTable
        .insertAndGetId { row ->
          row[UserChoiceTable.uuidCol] = UUID.randomUUID()
          row[UserChoiceTable.userUuid] = UUID.fromString(uuid)
          row[UserChoiceTable.fromfqName] = fromfqName
          row[UserChoiceTable.fromTitle] = fromTitle
          row[UserChoiceTable.tofqName] = slideChoice.fqName
          row[UserChoiceTable.toTitle] = slideChoice.title
          row[UserChoiceTable.choiceText] = slideChoice.choiceText
          row[UserChoiceTable.reason] = reason
        }.value

      updateLastSlide(uuid, slideChoice.fqName)

      val slide = findSlideForUser(uuid, HistoryWalkServer.masterSlides.get())
      slideData(uuid, slide)
    }

  override suspend fun goBackInTime(parentTitle: ParentTitle) =
    transaction {
      val uuid = call.userId.uuid
      updateLastSlide(uuid, parentTitle.fqName)
      val slide = findSlideForUser(uuid, HistoryWalkServer.masterSlides.get())
      slideData(uuid, slide)
    }

  companion object : KLogging() {
    private const val ltEscape = "---LT---"
    private const val gtEscape = "---GT---"
    private const val dquoteEscape = "---DQ---"
    private const val squoteEscape = "---SQ---"

    fun updateLastSlide(uuid: String, fqName: String) {
      UsersTable
        .update({ UsersTable.uuidCol eq UUID.fromString(uuid) }) { row ->
          row[UsersTable.lastfqName] = fqName
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
    }

    private fun slideData(uuid: String, slide: Slide): SlideData {

      val content =
        slide.content
          .splitToSequence("\n")
          .map { line -> line.trim() }  // Get rid of leading/trailing whitespace
          .joinToString("\n")
          .transformText()                     // Transform markdown to html

      val choices =
        slide.choices.map { (choice, slide) ->
          SlideChoice(choice, slide.fqName, slide.title)
        }

      val parentTitles =
        mutableListOf<ParentTitle>()
          .also { parentTitles ->
            var currSlide = slide.parentSlide
            while (currSlide != null) {
              parentTitles += ParentTitle(currSlide.fqName, currSlide.title)
              currSlide = currSlide.parentSlide
            }
          }
          .reversed()

      return slide.run {
        val count = slideCount(uuid)
        val showResetButton = EnvVar.SHOW_RESET_BUTTON.getEnv(false)
        SlideData(fqName, title, content, success, choices, verticalChoices, parentTitles, count, showResetButton)
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
