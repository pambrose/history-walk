package com.github.pambrose

import kotlinx.serialization.Serializable

@Serializable
data class Credentials(
  val username: String? = null,
  val password: String? = null,
)
