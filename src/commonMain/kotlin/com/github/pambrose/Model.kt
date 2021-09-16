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
  val choices: List<ChoiceTitle>,
  val orientation: ChoiceOrientation,
  val parentTitles: List<String>,
  val decisionCount: Long,
)

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