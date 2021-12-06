package com.github.pambrose

import com.github.pambrose.Utils.toUuid
import com.github.pambrose.Utils.transformText
import com.github.pambrose.slides.Slide
import com.pambrose.common.exposed.get
import mu.KLogging
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

object DbmsTxs : KLogging() {
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

  fun slideData(uuid: String, slide: Slide): SlideData {
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

    return slide
      .run {
        val count = decisionCount(uuid)
        val reset = EnvVar.SHOW_RESET_BUTTON.getEnv(false)
        SlideData(pathName, title, content, success, choices, verticalChoices, parentTitles, offset, count, reset)
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

  fun allUserSummaries() =
    transaction {
      UserDecisionCountsView
        .selectAll()
        .map { row ->
          UserSummary(
            row[UserDecisionCountsView.fullName],
            row[UserDecisionCountsView.email],
            row[UserDecisionCountsView.lastPathName],
            row[UserDecisionCountsView.decisionCount],
          )
        }
    }

  data class UserSummary(
    val fullName: String,
    val email: String,
    val lastPathName: String,
    val decisionCount: Int,
  )

}

