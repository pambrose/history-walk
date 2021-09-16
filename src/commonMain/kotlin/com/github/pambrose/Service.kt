package com.github.pambrose

import io.kvision.annotations.KVService

@KVService
interface IRegisterProfileService {
  suspend fun registerProfile(profile: Profile, password: String): Boolean
}

@KVService
interface IContentService {
  suspend fun currentSlide(title: String): SlideData
  suspend fun choose(fromTitle: String, abbrev: String, title: String): UserChoice
  suspend fun reason(fromTitle: String, abbrev: String, title: String, reason: String): String
}