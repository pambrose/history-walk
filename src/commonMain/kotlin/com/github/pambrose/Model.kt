@file:UseContextualSerialization(LocalDateTime::class)

package com.github.pambrose

import io.kvision.types.LocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseContextualSerialization

@Serializable
data class Profile(
  val id: Int? = null,
  val name: String? = null,
  val username: String? = null,
  val password: String? = null,
  val password2: String? = null
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
