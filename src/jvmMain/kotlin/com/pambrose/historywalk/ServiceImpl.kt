package com.pambrose.historywalk

import com.pambrose.historywalk.DbmsTxs.slideData
import com.pambrose.historywalk.DbmsTxs.updateLastSlide
import com.pambrose.historywalk.HistoryWalkServer.masterSlides
import com.pambrose.historywalk.User.Companion.findCurrentSlideForUser
import com.pambrose.historywalk.User.Companion.userInfoFor
import com.pambrose.historywalk.Utils.toUuid
import com.pambrose.historywalk.auth.SchoolConfig
import com.pambrose.historywalk.auth.userUuid
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.*
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class ContentService(
  private val call: ApplicationCall,
) : IContentService {
  override suspend fun getUserInfo() =
    userInfoFor(call.userUuid.toUuid(), SchoolConfig.teacherEmails) ?: UserInfo("Unknown", "Unknown")

  override suspend fun getCurrentSlide(): SlideData {
    logger.debug { "userUuid=${call.userUuid}" }
    val uuid = call.userUuid
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
      val uuid = call.userUuid
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
      val uuid = call.userUuid
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
      val uuid = call.userUuid
      updateLastSlide(uuid, parentTitle.pathName)
      val slide = findCurrentSlideForUser(uuid, masterSlides)
      slideData(uuid, slide)
    }

  companion object {
    private val logger = KotlinLogging.logger {}
  }
}
