package com.github.pambrose

import com.github.pambrose.DbmsTxs.slideData
import com.github.pambrose.DbmsTxs.updateLastSlide
import com.github.pambrose.HistoryWalkServer.masterSlides
import com.github.pambrose.User.Companion.findCurrentSlideForUser
import com.github.pambrose.Utils.toUuid
import com.google.inject.Inject
import io.ktor.server.application.*
import mu.two.KLogging
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

@Suppress("ACTUAL_WITHOUT_EXPECT")
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

@Suppress("ACTUAL_WITHOUT_EXPECT")
actual class ContentService : IContentService {
  @Inject
  lateinit var call: ApplicationCall

  override suspend fun getUserInfo() =
    transaction {
      val uuid = call.userId.uuid
      with(UsersTable) {
        selectAll()
          .where { id eq uuid.toUuid() }
          .map { row ->
            UserInfo(
              row[fullName],
              row[email],
            )
          }
          .firstOrNull() ?: UserInfo("Unknown", "Unknown")
      }
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
    advance: Boolean,
  ): UserChoice =
    transaction {
      // See if user has an entry for that transition
      val uuid = call.userId.uuid
      with(UserChoiceTable) {
        selectAll()
          .where {
            (userUuidRef eq uuid.toUuid()) and
              (fromPathName eq fromPathNameStr) and
              (toPathName eq slideChoice.toPathName)
          }
          .map { row ->
            UserChoice(
              row[fromPathName],
              row[fromTitle],
              SlideChoice(
                row[choiceText],
                row[toPathName],
                row[toTitle],
                row[deadEnd],
                0,
                false,
              ),
              row[reason],
            )
          }
          .firstOrNull() ?: UserChoice(fromPathNameStr, fromTitleStr, slideChoice, if (advance) "Advance" else "")
      }.also { userChoice ->
        if (userChoice.reason.isNotBlank()) {
          updateLastSlide(uuid, slideChoice.toPathName)
        }
      }
    }

  override suspend fun provideReason(
    fromPathNameStr: String,
    fromTitleStr: String,
    slideChoice: SlideChoice,
    reasonStr: String,
  ): SlideData =
    transaction {
      val uuid = call.userId.uuid
      with(UserChoiceTable) {
        insertAndGetId { row ->
          row[userUuidRef] = uuid.toUuid()
          row[fromPathName] = fromPathNameStr
          row[fromTitle] = fromTitleStr
          row[toPathName] = slideChoice.toPathName
          row[toTitle] = slideChoice.toTitle
          row[deadEnd] = slideChoice.deadEnd
          row[choiceText] = slideChoice.choiceText
          row[reason] = reasonStr
        }.value
      }

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
