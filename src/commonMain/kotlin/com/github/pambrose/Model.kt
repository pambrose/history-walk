@file:UseContextualSerialization(LocalDateTime::class)

package com.github.pambrose

import io.kvision.types.LocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseContextualSerialization

@Serializable
data class Profile(
  //val id: Int,
  val name: String,
  val email: String,
  val password: String,
  val password2: String
)

@Serializable
data class Address(
  val id: Int? = 0,
  val firstName: String? = null,
  val lastName: String? = null,
  val email: String? = null,
  val phone: String? = null,
  val postalAddress: String? = null,
  val favourite: Boolean? = false,
  val createdAt: LocalDateTime? = null,
  val userId: Int? = null
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