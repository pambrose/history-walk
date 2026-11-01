package com.pambrose.historywalk

import com.pambrose.historywalk.HistoryWalkServer.masterSlides
import com.pambrose.historywalk.ReasonRule.wordCount
import com.pambrose.historywalk.User.Companion.findCurrentSlideForUser
import com.pambrose.historywalk.User.Companion.userInfoFor
import com.pambrose.historywalk.Utils.toUuid
import com.pambrose.historywalk.auth.SchoolConfig
import com.pambrose.historywalk.auth.userUuid
import com.pambrose.historywalk.dbms.DbmsTxs.slideData
import com.pambrose.historywalk.dbms.DbmsTxs.updateLastSlide
import com.pambrose.historywalk.dbms.UserChoiceTable
import com.pambrose.historywalk.slides.Slide
import com.pambrose.historywalk.slides.SlideDeck.Companion.ROOT
import dev.kilua.rpc.ServiceException
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.*
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * The RPC service for the signed-in user. The deck ([masterSlides]) and the user's stored position are the source of
 * truth: a choice must start from the user's current slide and be one of its forward choices, the target's path,
 * title, choice text and dead end come from the deck, and so does whether a choice advances without a reason. The
 * client's copies of those values are ignored. A refused call throws a [ServiceException], which Kilua passes to the
 * browser as a short message without logging it, so each refusal is logged here.
 */
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

  // fromTitleStr, slideChoice's fields other than toPathName, and advance are ignored: the deck supplies them.
  override suspend fun makeChoice(
    fromPathNameStr: String,
    fromTitleStr: String,
    slideChoice: SlideChoice,
    advance: Boolean,
  ): UserChoice =
    transaction {
      val uuid = call.userUuid
      val move = moveFor(uuid, fromPathNameStr, slideChoice.toPathName)
      val recorded = recordedReason(uuid, move)
      val reason = recorded ?: if (move.advance) ADVANCE else ""
      if (reason.isNotBlank()) {
        updateLastSlide(uuid, move.to.pathName)
      } else if (move.repeat) {
        // Already on the target, yet the move was neither recorded nor an advance: they got there some other way
        refuse(uuid, NOT_ON_SLIDE, "makeChoice ${move.describe()} unrecorded")
      }
      UserChoice(move.from.pathName, move.from.title, move.slideChoice(alreadyVisited = recorded != null), reason)
    }

  // fromTitleStr and slideChoice's fields other than toPathName are ignored: the deck supplies them.
  override suspend fun provideReason(
    fromPathNameStr: String,
    fromTitleStr: String,
    slideChoice: SlideChoice,
    reasonStr: String,
  ): SlideData =
    transaction {
      val uuid = call.userUuid
      val reasonText = reasonStr.trim()
      ReasonRule.problemWith(reasonText)?.also { problem ->
        refuse(uuid, problem, "provideReason: ${reasonText.length} characters, ${wordCount(reasonText)} words")
      }

      val move = moveFor(uuid, fromPathNameStr, slideChoice.toPathName)
      if (move.advance)
        refuse(uuid, NO_REASON_NEEDED, "provideReason ${move.describe()}")

      if (move.repeat) {
        // Already on the target: accept only a repeat of the call that recorded this move, e.g. a second dialog
        if (recordedReason(uuid, move) == null)
          refuse(uuid, NOT_ON_SLIDE, "provideReason ${move.describe()} unrecorded")
        logger.info { "Repeated ${move.describe()} for $uuid; kept the first reason" }
      } else {
        // Postgres INSERT ... ON CONFLICT DO NOTHING: user_choices_unique decides, so a second call for a recorded
        // move keeps the first reason instead of failing, even when two calls race
        val inserted =
          with(UserChoiceTable) {
            insertIgnore { row ->
              row[userUuidRef] = uuid.toUuid()
              row[fromPathName] = move.from.pathName
              row[fromTitle] = move.from.title
              row[toPathName] = move.to.pathName
              row[toTitle] = move.to.title
              row[deadEnd] = move.to.isDeadEnd
              row[choiceText] = move.choiceText
              row[reason] = reasonText
            }.insertedCount
          }
        if (inserted > 0) {
          logger.info { "Recorded ${move.describe()} deadEnd=${move.to.isDeadEnd} for $uuid" }
        } else {
          logger.info { "Already recorded ${move.describe()} for $uuid; kept the first reason" }
        }
      }

      updateLastSlide(uuid, move.to.pathName)
      slideData(uuid, findCurrentSlideForUser(uuid, masterSlides))
    }

  override suspend fun goBackInTime(parentTitle: ParentTitle) =
    transaction {
      val uuid = call.userUuid
      val current = findCurrentSlideForUser(uuid, masterSlides)
      val target = parentTitle.pathName
      // ROOT is the client's "back to the start". The current slide itself is a no-op, so a repeated click is harmless.
      val allowed = target == ROOT || target == current.pathName || current.ancestors.any { it.pathName == target }
      if (!allowed)
        refuse(uuid, NOT_BEHIND_YOU, "goBackInTime from ${current.pathName} to $target")
      updateLastSlide(uuid, target)
      slideData(uuid, findCurrentSlideForUser(uuid, masterSlides))
    }

  override suspend fun versionCheck(versionCheckInfo: VersionCheckInfo): VersionCheckResponse =
    ServerVersionCheck.checkServerVersion(versionCheckInfo)

  /** A forward move in the deck: [choiceText] on [from] leads to [to]. [repeat]: the user already stands on [to]. */
  private class Move(
    val from: Slide,
    val choiceText: String,
    val to: Slide,
    val repeat: Boolean,
  ) {
    // As the client decides it: a slide with a single choice, go-back choices counted, moves on without a reason
    val advance get() = from.choices.size == 1

    fun slideChoice(alreadyVisited: Boolean) =
      SlideChoice(choiceText, to.pathName, to.title, to.isDeadEnd, offset = 0, alreadyVisited = alreadyVisited)

    fun describe() = "${from.pathName} -> ${to.pathName}"
  }

  /**
   * The move from [fromPathName] to [toPathName], checked against the deck and the user's stored position:
   * [fromPathName] must be the user's current slide, and [toPathName] one of its forward choices. A move to the
   * current slide from its parent is also returned, flagged as a [Move.repeat]: a second call for a move just made
   * (a double-click, a second reason dialog, or a retried transaction) then finds the user already there. The caller
   * decides whether a repeat is acceptable.
   */
  private fun moveFor(
    uuid: String,
    fromPathName: String,
    toPathName: String,
  ): Move {
    val current = findCurrentSlideForUser(uuid, masterSlides)
    val parent = current.parentSlide
    val (from, repeat) =
      when {
        fromPathName == current.pathName -> current to false
        parent != null && fromPathName == parent.pathName && toPathName == current.pathName -> parent to true
        else -> refuse(uuid, NOT_ON_SLIDE, "from $fromPathName to $toPathName, current ${current.pathName}")
      }
    val (choiceText, to) =
      from.choices.entries.firstOrNull { (_, slide) -> slide.offset == 0 && slide.pathName == toPathName }
        ?: refuse(uuid, NOT_A_CHOICE, "from ${from.pathName} to $toPathName")
    return Move(from, choiceText, to, repeat)
  }

  // The reason recorded for [move], or null if the user has not made it. Runs inside the caller's transaction.
  private fun recordedReason(
    uuid: String,
    move: Move,
  ): String? =
    with(UserChoiceTable) {
      select(reason)
        .where {
          (userUuidRef eq uuid.toUuid()) and
            (fromPathName eq move.from.pathName) and
            (toPathName eq move.to.pathName)
        }.map { it[reason] }
        .firstOrNull()
    }

  private fun refuse(
    uuid: String,
    message: String,
    detail: String,
  ): Nothing {
    logger.warn { "Refused $uuid: $message ($detail)" }
    throw ServiceException(message)
  }

  companion object {
    private val logger = KotlinLogging.logger {}

    /** makeChoice's reason for a choice that moves on without asking for one. */
    const val ADVANCE = "Advance"

    // The messages a refused call carries to the browser
    const val NOT_ON_SLIDE = "That choice is not on your current slide"
    const val NOT_A_CHOICE = "That slide has no such choice"
    const val NO_REASON_NEEDED = "That choice takes no reason"
    const val NOT_BEHIND_YOU = "You can only go back to a slide on your path"
  }
}
