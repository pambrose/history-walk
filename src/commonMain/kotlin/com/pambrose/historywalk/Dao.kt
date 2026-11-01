@file:UseContextualSerialization(LocalDateTime::class)

package com.pambrose.historywalk

import io.kvision.types.LocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseContextualSerialization

@Serializable
data class UserInfo(
  val fullName: String,
  val email: String,
  val pictureUrl: String? = null,
  val role: Role = Role.STUDENT,
)

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

  val hasParents: Boolean
    get() = parentTitles.isNotEmpty()

  val hasOneParent: Boolean
    get() = parentTitles.size == 1
}

@Serializable
data class ParentTitle(val pathName: String, val title: String)

@Serializable
data class SlideChoice(
  val choiceText: String,
  val toPathName: String,
  val toTitle: String,
  val deadEnd: Boolean,
  val offset: Int,
  val alreadyVisited: Boolean,
)

@Serializable
data class UserChoice(
  val fromPathName: String,
  val fromTitle: String,
  val slideChoice: SlideChoice,
  val reason: String,
)
