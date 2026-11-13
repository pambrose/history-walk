package com.pambrose.historywalk.dbms

import com.pambrose.common.exposed.get
import com.pambrose.historywalk.EnvVar.DISPLAY_CONSECUTIVE_CORRECT_DECISIONS
import com.pambrose.historywalk.EnvVar.SHOW_RESET_BUTTON
import com.pambrose.historywalk.ParentTitle
import com.pambrose.historywalk.SlideChoice
import com.pambrose.historywalk.SlideData
import com.pambrose.historywalk.Utils.toUuid
import com.pambrose.historywalk.Utils.transformText
import com.pambrose.historywalk.slides.Slide
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * The walk's queries: a user's slide as the page draws it, their place and moves, and the class pages' reads of the
 * progress views.
 */
object DbmsTxs {
  private val logger = KotlinLogging.logger {}

  /**
   * [slide] as the user [uuid] sees it: its content rendered to HTML, each choice marked when the user has already
   * taken it, their decision count and streak of correct decisions, and the display settings from the environment.
   */
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
          deadEnd = slide.leadsToDeadEnd,
          offset = slide.offset,
          alreadyVisited = slide.title in alreadyVisited,
        )
      }

    val parentTitles = slide.ancestors.map { ParentTitle(it.pathName, it.title) }

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

  /**
   * Puts the user [uuid] on the slide at [pathName]. Runs in the caller's transaction, having none of its own, and
   * fails when no user has [uuid].
   */
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

  /** Deletes every move of the user [uuid], for the reset. Runs in the caller's transaction, having none of its own. */
  fun deleteChoices(uuid: String) {
    UserChoiceTable
      .deleteWhere { UserChoiceTable.userUuidRef eq uuid.toUuid() }
      .also { count ->
        logger.info { "Deleted $count records for uuid: $uuid" }
      }
  }

  /** The number of moves the user [uuid] has recorded, each with its reason; a "Continue" is never recorded. */
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

  /**
   * The user [uuid]'s consecutive correct decisions: the moves recorded since their last wrong turn (a move marked
   * dead end), or every move when they have made none.
   */
  fun correctDecisionStreak(uuid: String) =
    transaction {
      val wrongDecisions =
        with(UserChoiceTable) {
          selectAll()
            .where { userUuidRef eq uuid.toUuid() and (deadEnd eq true) }
            .count()
            .toInt()
        }

      if (wrongDecisions == 0) {
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

  /** Every row of [UserReasonsView], for the reasons page: each counted move, its reason, and who made it. */
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
              choiceText = row[choiceText],
              reason = row[reason],
            )
          }
      }
    }

  /** Every row of [UserDecisionCountsView], for the summary page: each user, their slide, and their counted moves. */
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

  /**
   * The counted moves that reached a slide titled [successSlideTitle], one per move, so a user may appear more than
   * once. The summary page's Success column takes their ids.
   */
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

  /** A row of [UserVisitsView]: the mover's [id] and the title of the slide a counted move reached ([toSlide]). */
  data class UserVisits(
    val id: String,
    val toSlide: String,
  )

  /** A row of [UserDecisionCountsView]: a user the summary page lists, the slide they stand on, their counted moves. */
  data class UserSummary(
    val id: String,
    val fullName: String,
    val email: String,
    val lastPathName: String,
    val decisionCount: Int,
  )

  /** A row of [UserReasonsView]: a counted move, the reason given for it, and who made it. */
  data class UserReason(
    val id: String,
    val fullName: String,
    val email: String,
    val fromPathName: String,
    val toPathName: String,
    val fromTitle: String,
    val toTitle: String,
    val choiceText: String,
    val reason: String,
  )
}
