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
enum class ElementType { TEXT, IMAGE }

@Serializable
data class ElementData(
  val elementType: ElementType,
  val content: String,
  val width: Int = 0,
  val height: Int = 0
)

@Serializable
data class SlideDeckData(
  val title: String,
  val elements: List<ElementData>,
  val success: Boolean,
  val choices: List<ChoiceTitle>,
  val verticalChoices: Boolean,
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
