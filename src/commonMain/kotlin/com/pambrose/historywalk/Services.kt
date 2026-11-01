package com.pambrose.historywalk

import dev.kilua.rpc.annotations.RpcService

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

  /** Whether the calling page's bundle still matches the server (ServerVersionCheck), polled by ClientVersionCheck. */
  suspend fun versionCheck(versionCheckInfo: VersionCheckInfo): VersionCheckResponse
}
