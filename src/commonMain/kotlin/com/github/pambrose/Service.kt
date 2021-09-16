package com.github.pambrose

import io.kvision.annotations.KVService

@KVService
interface IRegisterUserService {
  suspend fun registerUser(registerData: RegisterData): Boolean
}

@KVService
interface IContentService {
  suspend fun currentSlide(): SlideData
  suspend fun choose(fromTitle: String, abbrev: String, title: String): UserChoice
  suspend fun reason(fromTitle: String, abbrev: String, title: String, reason: String): SlideData
  suspend fun goBack(title: String): SlideData
}