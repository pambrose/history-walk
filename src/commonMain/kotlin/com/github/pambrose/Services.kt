package com.github.pambrose

import io.kvision.annotations.KVService

@KVService
interface IRegisterUserService {
  suspend fun registerUser(registerData: RegisterData): Boolean
}

@KVService
interface IContentService {
  suspend fun getUserInfo(): UserInfo
  suspend fun getCurrentSlide(): SlideData
  suspend fun makeChoice(
    fromPathNameStr: String,
    fromTitleStr: String,
    slideChoice: SlideChoice,
    advance: Boolean
  ): UserChoice

  suspend fun provideReason(
    fromPathNameStr: String,
    fromTitleStr: String,
    slideChoice: SlideChoice,
    reasonStr: String
  ): SlideData

  suspend fun goBackInTime(parentTitle: ParentTitle): SlideData
}