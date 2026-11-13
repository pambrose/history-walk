@file:UseContextualSerialization(LocalDateTime::class)

package com.pambrose.historywalk

import io.kvision.types.LocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseContextualSerialization
import kotlin.math.abs

/**
 * The signed-in user as the page's header shows them: name, address, Google picture (if any), and [role], which is a
 * student's for a teacher while teachers see the student view.
 */
@Serializable
data class UserInfo(
  val fullName: String,
  val email: String,
  val pictureUrl: String? = null,
  val role: Role = Role.STUDENT,
)

/**
 * A slide as the page draws it: its content (HTML), its [choices], and its ancestors ([parentTitles], the root first),
 * with the user's decision count and streak of correct decisions and the settings that shape the page.
 */
@Serializable
data class SlideData(
  val pathName: String,
  val title: String,
  val content: String,
  val success: Boolean,
  val choices: List<SlideChoice>,
  val verticalChoices: Boolean,
  val parentTitles: List<ParentTitle>,
  val offset: Int,
  val displayTitle: Boolean,
  val decisionCount: Int,
  val displayConsecutiveCorrectDecisions: Boolean,
  val consecutiveCorrectDecisions: Int,
  val showResetButton: Boolean,
) {
  val failure: Boolean
    get() = !success && choices.isEmpty()

  /** The ancestor a go-back [choice] returns to: an offset of -1 is this slide's parent, -2 its grandparent, and so on. */
  fun goBackTarget(choice: SlideChoice): ParentTitle {
    val steps = abs(choice.offset)
    require(steps in 1..parentTitles.size) {
      "Cannot go back $steps from $pathName with ${parentTitles.size} ancestors"
    }
    return parentTitles[parentTitles.size - steps]
  }
}

/** A slide's path name and title, as a go-back button shows an ancestor and as the target of a go-back. */
@Serializable
data class ParentTitle(
  val pathName: String,
  val title: String,
)

/**
 * A choice on a slide: its text and the slide it leads to, whether that is a wrong turn ([deadEnd]), the go-back
 * [offset] (0 for a forward choice, -n for the n-th ancestor), and whether the user has already made it.
 */
@Serializable
data class SlideChoice(
  val choiceText: String,
  val toPathName: String,
  val toTitle: String,
  val deadEnd: Boolean,
  val offset: Int,
  val alreadyVisited: Boolean,
)

/**
 * A move as makeChoice answers it, with its [reason]: the one recorded if the user already made the move, "Advance"
 * for a choice that moves on without one, or empty when the page must ask for one.
 */
@Serializable
data class UserChoice(
  val fromPathName: String,
  val fromTitle: String,
  val slideChoice: SlideChoice,
  val reason: String,
)
