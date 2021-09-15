package com.github.pambrose

import io.kvision.annotations.KVService

@KVService
interface IProfileService {
  suspend fun getProfile(): Profile
}

@KVService
interface IRegisterProfileService {
  suspend fun registerProfile(profile: Profile, password: String): Boolean
}

@KVService
interface IContentService {
  suspend fun hello(): String
  suspend fun currentSlide(title: String): SlideData
  suspend fun choose(fromTitle: String, choice: String, choiceTitle: String): ChoiceReason
  suspend fun reason(choiceId: String, reason: String): String
}