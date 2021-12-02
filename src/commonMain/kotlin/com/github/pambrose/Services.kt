package com.github.pambrose

import io.kvision.annotations.KVService

@KVService
interface IRegisterUserService {
  suspend fun registerUser(registerData: RegisterData): Boolean
}

@KVService
interface IContentService {
  suspend fun getCurrentSlide(): SlideData
  suspend fun makeChoice(
    fromPathName: String,
    fromTitle: String,
    slideChoice: SlideChoice,
    advance: Boolean
  ): UserChoice

  suspend fun provideReason(
    fromPathName: String,
    fromTitle: String,
    slideChoice: SlideChoice,
    reason: String
  ): SlideData

  suspend fun goBackInTime(parentTitle: ParentTitle): SlideData
}