@file:UseContextualSerialization(LocalDateTime::class)

package com.github.pambrose

import io.kvision.types.LocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseContextualSerialization

@Serializable
data class Profile(
  val uuid: String,
  val name: String,
  val email: String,
  val password: String,
  val password2: String
)

@Serializable
data class SlideData(
  val title: String,
  val contents: String,
  val choices: List<ChoiceTitle>,
  val orientation: ChoiceOrientation,
  val parentTitles: List<String>,
  val currentScore: Int,
)

@Serializable
data class ChoiceTitle(val choice: String, val title: String)

@Serializable
data class ChoiceReason(val choiceId: String, val reason: String)

@Serializable
enum class ChoiceOrientation { VERTICAL, HORIZONTAL }