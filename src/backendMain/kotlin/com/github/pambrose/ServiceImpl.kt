package com.github.pambrose

import com.github.pambrose.User.Companion.findCurrentSlideForUser
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

  override suspend fun getUserInfo() =
    transaction {
      val uuid = call.userId.uuid
      UsersTable
        .select { UsersTable.uuidCol eq UUID.fromString(uuid) }
        .map { row ->
          UserInfo(
            row[UsersTable.fullName],
            row[UsersTable.email],
          )
        }
        .firstOrNull() ?: UserInfo("Unknown", "Unknown")
    }

  override suspend fun getCurrentSlide(): SlideData {
    logger.debug { "userId=${call.userId}" }
    val uuid = call.userId.uuid
    val slide = findCurrentSlideForUser(uuid, HistoryWalkServer.masterSlides.get())
    return slideData(uuid, slide)
  }

  override suspend fun makeChoice(
    fromPathName: String,
    fromTitle: String,
    slideChoice: SlideChoice,
    advance: Boolean
  ): UserChoice =
    transaction {
      // See if user has an entry for that transition
      val uuid = call.userId.uuid
      (UserChoiceTable
        .select { (UserChoiceTable.userUuid eq UUID.fromString(uuid)) and (UserChoiceTable.fromPathName eq fromPathName) and (UserChoiceTable.toPathName eq slideChoice.pathName) }
        .map { row ->
          UserChoice(
            row[UserChoiceTable.fromPathName],
            row[UserChoiceTable.fromTitle],
            SlideChoice(
              row[UserChoiceTable.choiceText],
              row[UserChoiceTable.toPathName],
              row[UserChoiceTable.toTitle],
              0
            ),
            row[UserChoiceTable.reason],
          )
        }
        .firstOrNull() ?: UserChoice(fromPathName, fromTitle, slideChoice, if (advance) "Advance" else "")
          )
        .also { userChoice ->
          if (userChoice.reason.isNotBlank()) {
            updateLastSlide(uuid, slideChoice.pathName)
          }
        }
    }

  override suspend fun provideReason(
    fromPathName: String,
    fromTitle: String,
    slideChoice: SlideChoice,
    reason: String
  ) =
    transaction {
      val uuid = call.userId.uuid
      UserChoiceTable
        .insertAndGetId { row ->
          row[UserChoiceTable.uuidCol] = UUID.randomUUID()
          row[UserChoiceTable.userUuid] = UUID.fromString(uuid)
          row[UserChoiceTable.fromPathName] = fromPathName
          row[UserChoiceTable.fromTitle] = fromTitle
          row[UserChoiceTable.toPathName] = slideChoice.pathName
          row[UserChoiceTable.toTitle] = slideChoice.title
          row[UserChoiceTable.choiceText] = slideChoice.choiceText
          row[UserChoiceTable.reason] = reason
        }.value

      updateLastSlide(uuid, slideChoice.pathName)

      val slide = findCurrentSlideForUser(uuid, HistoryWalkServer.masterSlides.get())
      slideData(uuid, slide)
    }

  override suspend fun goBackInTime(parentTitle: ParentTitle) =
    transaction {
      val uuid = call.userId.uuid
      updateLastSlide(uuid, parentTitle.pathName)
      val slide = findCurrentSlideForUser(uuid, HistoryWalkServer.masterSlides.get())
      slideData(uuid, slide)
    }

  companion object : KLogging() {
    private const val ltEscape = "---LT---"
    private const val gtEscape = "---GT---"
    private const val dquoteEscape = "---DQ---"
    private const val squoteEscape = "---SQ---"

    fun updateLastSlide(uuid: String, pathName: String) {
      UsersTable
        .update({ UsersTable.uuidCol eq UUID.fromString(uuid) }) { row ->
          row[UsersTable.lastPathName] = pathName
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
          SlideChoice(choice, slide.pathName, slide.title, slide.offset)
        }

      val parentTitles =
        mutableListOf<ParentTitle>()
          .also { parentTitles ->
            var currSlide = slide.parentSlide
            while (currSlide != null) {
              parentTitles += ParentTitle(currSlide.pathName, currSlide.title)
              currSlide = currSlide.parentSlide
            }
          }
          .reversed()

      return slide.run {
        val count = slideCount(uuid)
        val reset = EnvVar.SHOW_RESET_BUTTON.getEnv(false)
        SlideData(pathName, title, content, success, choices, verticalChoices, parentTitles, offset, count, reset)
      }
    }

    private fun slideCount(uuid: String) =
      transaction {
        UserChoiceTable
          .slice(Count(UserChoiceTable.id))
          .select { UserChoiceTable.userUuid eq UUID.fromString(uuid) }
          .map { it[0] as Long }
          .first()
          .toInt()
      }
  }
}
