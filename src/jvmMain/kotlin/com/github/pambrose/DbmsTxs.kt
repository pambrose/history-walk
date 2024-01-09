package com.github.pambrose

import com.github.pambrose.EnvVar.DISPLAY_CONSECUTIVE_CORRECT_DECISIONS
import com.github.pambrose.EnvVar.SHOW_RESET_BUTTON
import com.github.pambrose.Utils.toUuid
import com.github.pambrose.Utils.transformText
import com.github.pambrose.slides.Slide
import com.pambrose.common.exposed.get
import kotlinx.datetime.LocalDateTime
import mu.two.KLogging
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

object DbmsTxs : KLogging() {
  fun slideData(
    uuid: String,
    slide: Slide,
  ): SlideData {
    val content =
      slide.content
        .splitToSequence("\n")
        .map { line -> line.trim() }  // Get rid of leading/trailing whitespace
        .joinToString("\n")
        .transformText()                     // Transform markdown to html

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
          choice,
          slide.pathName,
          slide.title,
          !slide.hasChoices && !slide.success,
          slide.offset,
          slide.title in alreadyVisited,
        )
      }

    val parentTitles =
      mutableListOf<ParentTitle>()
        .also { parentTitles ->
          var currSlide = slide.parentSlide
          while (currSlide != null) {
            parentTitles += ParentTitle(
              currSlide.pathName,
              currSlide.title,
            )
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
        logger.info("Deleted $count records for uuid: $uuid")
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
              row[id].toString(),
              row[fullName],
              row[email],
              row[fromPathName],
              row[toPathName],
              row[fromTitle],
              row[toTitle],
              row[reason],
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
              row[id].toString(),
              row[fullName],
              row[email],
              row[lastPathName],
              row[decisionCount],
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
              row[id].toString(),
              row[toTitle],
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
