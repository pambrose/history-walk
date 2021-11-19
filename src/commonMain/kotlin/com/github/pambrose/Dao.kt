@file:UseContextualSerialization(LocalDateTime::class)

package com.github.pambrose

import io.kvision.types.LocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseContextualSerialization

@Serializable
data class RegisterData(
  val fullName: String,
  val email: String,
  val password: String,
  val password2: String,
)

@Serializable
data class UserId(
  val uuid: String,
)

@Serializable
data class SlideData(
  val title: String,
  val contents: String,
  val success: Boolean,
  val choices: List<ChoiceTitle>,
  val orientation: ChoiceOrientation,
  val parentTitles: List<String>,
  val decisionCount: Long,
) {
  val failure: Boolean
    get() = !success && choices.isEmpty()

  val hasParents: Boolean
    get() = parentTitles.isNotEmpty()

  val hasOneParent: Boolean
    get() = parentTitles.size == 1
}

@Serializable
data class ChoiceTitle(val abbrev: String, val title: String)

@Serializable
data class UserChoice(
  val fromTitle: String,
  val abbrev: String,
  val title: String,
  val reason: String,
)

@Serializable
enum class ChoiceOrientation { VERTICAL, HORIZONTAL }