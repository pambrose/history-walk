package com.github.pambrose

import com.github.pambrose.EnvVar.DISPLAY_CONSECUTIVE_CORRECT_DECISIONS
import com.github.pambrose.EnvVar.SHOW_RESET_BUTTON
import com.github.pambrose.Utils.toUuid
import com.github.pambrose.Utils.transformText
import com.github.pambrose.slides.Slide
import com.pambrose.common.exposed.get
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object DbmsTxs {
  private val logger = KotlinLogging.logger {}

  fun slideData(
    uuid: String,
    slide: Slide,
  ): SlideData {
    val content =
      slide.content
        .splitToSequence("\n")
        .joinToString("\n") { line -> line.trim() }
        .transformText()

    val alreadyVisited =
      transaction {
        with(UserChoiceTable) {
          select(UserChoiceTable.toTitle)
            .where { UserChoiceTable.userUuidRef eq uuid.toUuid() and (UserChoiceTable.fromPathName eq slide.pathName) }
            .map { it[UserChoiceTable.toTitle] }
        }
      }

    val choices =
      slide.choices.map { (choice, slide) ->
        SlideChoice(
          choiceText = choice,
          toPathName = slide.pathName,
          toTitle = slide.title,
          deadEnd = !slide.hasChoices && !slide.success,
          offset = slide.offset,
          alreadyVisited = slide.title in alreadyVisited,
        )
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

    return slide
      .run {
        SlideData(
          pathName = pathName,
          title = title,
          content = content,
          success = success,
          choices = choices,
          verticalChoices = verticalChoices,
          parentTitles = parentTitles,
          offset = offset,
          displayTitle = displayTitle,
          decisionCount = decisionCount(uuid),
          displayConsecutiveCorrectDecisions = DISPLAY_CONSECUTIVE_CORRECT_DECISIONS.getEnv(true),
          consecutiveCorrectDecisions = correctDecisionStreak(uuid),
          showResetButton = SHOW_RESET_BUTTON.getEnv(false),
        )
      }
  }

  fun updateLastSlide(
    uuid: String,
    pathName: String,
  ) {
    UsersTable
      .update({ UsersTable.id eq uuid.toUuid() }) { row ->
        row[UsersTable.lastPathName] = pathName
      }.also { count ->
        if (count != 1)
          error("Missing uuid: $uuid")
      }
  }

  fun deleteChoices(uuid: String) {
    UserChoiceTable
      .deleteWhere { UserChoiceTable.userUuidRef eq uuid.toUuid() }
      .also { count ->
        logger.info { "Deleted $count records for uuid: $uuid" }
      }
  }

  fun decisionCount(uuid: String) =
    transaction {
      with(UserChoiceTable) {
        select(Count(id))
          .where { userUuidRef eq uuid.toUuid() }
          .map { it[0] as Long }
          .first()
          .toInt()
      }
    }

  fun correctDecisionStreak(uuid: String) =
    transaction {
      val wrongDecsions =
        with(UserChoiceTable) {
          selectAll()
            .where { userUuidRef eq uuid.toUuid() and (deadEnd eq true) }
            .count()
            .toInt()
        }

      if (wrongDecsions == 0) {
        with(UserChoiceTable) {
          selectAll()
            .where { userUuidRef eq uuid.toUuid() and (deadEnd eq false) }
            .count()
            .toInt()
        }
      } else {
        // We know this will return an answer because we know it is non-zero from above
        // Find the time of the last wrong decision
        val lastWrongDecision =
          with(UserChoiceTable) {
            select(created.max())
              .where { userUuidRef eq uuid.toUuid() and (deadEnd eq true) }
              .map { it[0] as LocalDateTime }
              .first()
          }
        // Now count the number of correct answers since that time
        with(UserChoiceTable) {
          select(Count(created))
            .where { userUuidRef eq uuid.toUuid() and (created greater lastWrongDecision) }
            .map { it[0] as Long }
            .first()
            .toInt()
        }
      }
    }

  fun allUserReasons() =
    transaction {
      with(UserReasonsView) {
        selectAll()
          .map { row ->
            UserReason(
              id = row[id].toString(),
              fullName = row[fullName],
              email = row[email],
              fromPathName = row[fromPathName],
              toPathName = row[toPathName],
              fromTitle = row[fromTitle],
              toTitle = row[toTitle],
              reason = row[reason],
            )
          }
      }
    }

  fun allUserSummaries() =
    transaction {
      with(UserDecisionCountsView) {
        selectAll()
          .map { row ->
            UserSummary(
              id = row[id].toString(),
              fullName = row[fullName],
              email = row[email],
              lastPathName = row[lastPathName],
              decisionCount = row[decisionCount],
            )
          }
      }
    }

  fun allSuccessUsers(successSlideTitle: String) =
    transaction {
      with(UserVisitsView) {
        selectAll()
          .where { toTitle eq successSlideTitle }
          .map { row ->
            UserVisits(
              id = row[id].toString(),
              toSlide = row[toTitle],
            )
          }
      }
    }

  data class MaxDeadEndDate(
    val maxDeadEndDate: LocalDateTime,
  )

  data class UserVisits(
    val id: String,
    val toSlide: String,
  )

  data class UserSummary(
    val id: String,
    val fullName: String,
    val email: String,
    val lastPathName: String,
    val decisionCount: Int,
  )

  data class UserReason(
    val id: String,
    val fullName: String,
    val email: String,
    val fromPathName: String,
    val toPathName: String,
    val fromTitle: String,
    val toTitle: String,
    val reason: String,
  )
}
