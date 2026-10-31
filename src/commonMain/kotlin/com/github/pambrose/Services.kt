package com.github.pambrose

import dev.kilua.rpc.annotations.RpcService

@RpcService
interface IRegisterUserService {
  suspend fun registerUser(registerData: RegisterData): Boolean
}

@RpcService
interface IContentService {
  suspend fun getUserInfo(): UserInfo

  suspend fun getCurrentSlide(): SlideData

  suspend fun makeChoice(
    fromPathNameStr: String,
    fromTitleStr: String,
    slideChoice: SlideChoice,
    advance: Boolean,
  ): UserChoice

  suspend fun provideReason(
    fromPathNameStr: String,
    fromTitleStr: String,
    slideChoice: SlideChoice,
    reasonStr: String,
  ): SlideData

  suspend fun goBackInTime(parentTitle: ParentTitle): SlideData
}
