package com.github.pambrose

import io.kvision.annotations.KVService

@KVService
interface IRegisterUserService {
  suspend fun registerUser(registerData: RegisterData): Boolean
}

@KVService
interface IContentService {
  suspend fun getCurrentSlide(): SlideData
  suspend fun makeChoice(fromTitle: String, abbrev: String, toTitle: String, advance: Boolean): UserChoice
  suspend fun provideReason(fromTitle: String, abbrev: String, toTitle: String, reason: String): SlideData
  suspend fun goBackInTime(title: String): SlideData
}