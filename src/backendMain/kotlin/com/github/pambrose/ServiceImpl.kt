package com.github.pambrose

import com.github.pambrose.DbmsTxs.slideData
import com.github.pambrose.DbmsTxs.updateLastSlide
import com.github.pambrose.HistoryWalkServer.masterSlides
import com.github.pambrose.User.Companion.findCurrentSlideForUser
import com.github.pambrose.Utils.toUuid
import com.google.inject.Inject
import io.ktor.server.application.*
import mu.KLogging
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

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
        .select { UsersTable.id eq uuid.toUuid() }
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
    val slide = findCurrentSlideForUser(uuid, masterSlides)
    return slideData(uuid, slide)
  }

  override suspend fun makeChoice(
    fromPathNameStr: String,
    fromTitleStr: String,
    slideChoice: SlideChoice,
    advance: Boolean
  ): UserChoice =
    transaction {
      // See if user has an entry for that transition
      val uuid = call.userId.uuid
      (UserChoiceTable
        .select { (UserChoiceTable.userUuidRef eq uuid.toUuid()) and (UserChoiceTable.fromPathName eq fromPathNameStr) and (UserChoiceTable.toPathName eq slideChoice.toPathName) }
        .map { row ->
          UserChoice(
            row[UserChoiceTable.fromPathName],
            row[UserChoiceTable.fromTitle],
            SlideChoice(
              row[UserChoiceTable.choiceText],
              row[UserChoiceTable.toPathName],
              row[UserChoiceTable.toTitle],
              row[UserChoiceTable.deadEnd],
              0,
              false
            ),
            row[UserChoiceTable.reason],
          )
        }
        .firstOrNull() ?: UserChoice(fromPathNameStr, fromTitleStr, slideChoice, if (advance) "Advance" else "")
          )
        .also { userChoice ->
          if (userChoice.reason.isNotBlank()) {
            updateLastSlide(uuid, slideChoice.toPathName)
          }
        }
    }

  override suspend fun provideReason(
    fromPathNameStr: String,
    fromTitleStr: String,
    slideChoice: SlideChoice,
    reasonStr: String
  ) =
    transaction {
      val uuid = call.userId.uuid
      UserChoiceTable
        .insertAndGetId { row ->
          row[userUuidRef] = uuid.toUuid()
          row[fromPathName] = fromPathNameStr
          row[fromTitle] = fromTitleStr
          row[toPathName] = slideChoice.toPathName
          row[toTitle] = slideChoice.toTitle
          row[deadEnd] = slideChoice.deadEnd
          row[choiceText] = slideChoice.choiceText
          row[reason] = reasonStr
        }.value

      logger.info { "Inserted $fromTitleStr to ${slideChoice.toTitle} ${slideChoice.deadEnd}" }
      updateLastSlide(uuid, slideChoice.toPathName)

      val slide = findCurrentSlideForUser(uuid, masterSlides)
      slideData(uuid, slide)
    }

  override suspend fun goBackInTime(parentTitle: ParentTitle) =
    transaction {
      val uuid = call.userId.uuid
      updateLastSlide(uuid, parentTitle.pathName)
      val slide = findCurrentSlideForUser(uuid, masterSlides)
      slideData(uuid, slide)
    }

  companion object : KLogging()
}
