package com.github.pambrose

import com.github.pambrose.EnvVar.DISPLAY_CONSECUTIVE_CORRECT_DECISIONS
import com.github.pambrose.EnvVar.SHOW_RESET_BUTTON
import com.github.pambrose.Utils.toUuid
import com.github.pambrose.Utils.transformText
import com.github.pambrose.slides.Slide
import com.pambrose.common.exposed.get
import kotlinx.datetime.LocalDateTime
import mu.KLogging
import org.jetbrains.exposed.sql.Count
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.max
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

object DbmsTxs : KLogging() {
  fun slideData(uuid: String, slide: Slide): SlideData {
    val content =
      slide.content
        .splitToSequence("\n")
        .map { line -> line.trim() }  // Get rid of leading/trailing whitespace
        .joinToString("\n")
        .transformText()                     // Transform markdown to html

    val choices =
      slide.choices.map { (choice, slide) ->
        SlideChoice(choice, slide.pathName, slide.title, !slide.hasChoices && !slide.success, slide.offset)
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
          pathName,
          title,
          content,
          success,
          choices,
          verticalChoices,
          parentTitles,
          offset,
          displayTitle,
          decisionCount(uuid),
          DISPLAY_CONSECUTIVE_CORRECT_DECISIONS.getEnv(true),
          correctDecisionStreak(uuid),
          SHOW_RESET_BUTTON.getEnv(false),
        )
      }
  }

  fun updateLastSlide(uuid: String, pathName: String) {
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
      UserChoiceTable
        .slice(Count(UserChoiceTable.id))
        .select { UserChoiceTable.userUuidRef eq uuid.toUuid() }
        .map { it[0] as Long }
        .first()
        .toInt()
    }

  fun correctDecisionStreak(uuid: String) =
    transaction {

      val wrongDecsions =
        UserChoiceTable
          .select { UserChoiceTable.userUuidRef eq uuid.toUuid() and (UserChoiceTable.deadEnd eq true) }
          .count().toInt()

      if (wrongDecsions == 0) {
        UserChoiceTable
          .select { UserChoiceTable.userUuidRef eq uuid.toUuid() and (UserChoiceTable.deadEnd eq false) }
          .count().toInt()
      } else {
        // We know this will return an answer because we know it is non-zero from above
        // Find the time of the last wrong decision
        val lastWrongDecision =
          UserChoiceTable
            .slice(UserChoiceTable.created.max())
            .select { UserChoiceTable.userUuidRef eq uuid.toUuid() and (UserChoiceTable.deadEnd eq true) }
            .map { it[0] as LocalDateTime }
            .first()

        // Now count the number of correct answers since that time
        UserChoiceTable
          .slice(Count(UserChoiceTable.created))
          .select { UserChoiceTable.userUuidRef eq uuid.toUuid() and (UserChoiceTable.created greater lastWrongDecision) }
          .map { it[0] as Long }
          .first()
          .toInt()
      }
    }

  fun allUserSummaries() =
    transaction {
      UserDecisionCountsView
        .selectAll()
        .map { row ->
          UserSummary(
            row[UserDecisionCountsView.id].toString(),
            row[UserDecisionCountsView.fullName],
            row[UserDecisionCountsView.email],
            row[UserDecisionCountsView.lastPathName],
            row[UserDecisionCountsView.decisionCount],
          )
        }
    }

  fun allSuccessUsers(successSlideTitle: String) =
    transaction {
      UserVisitsView
        .select { UserVisitsView.toTitle eq successSlideTitle }
        .map { row ->
          UserVisits(
            row[UserVisitsView.id].toString(),
            row[UserVisitsView.toTitle],
          )
        }
    }

  data class MaxDeadEndDate(
    val maxDeadEndDate: LocalDateTime
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
}