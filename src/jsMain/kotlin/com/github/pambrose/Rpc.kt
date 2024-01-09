package com.github.pambrose

import com.github.pambrose.ClientUtils.withAuth
import io.kvision.remote.getService

object Rpc {
  private val registerUserService = getService<IRegisterUserService>()
  private val contentService = getService<IContentService>()

  suspend fun registerUser(registerData: RegisterData) =
    try {
      registerUserService.registerUser(registerData)
    } catch (e: Exception) {
      console.log(e)
      false
    }

  suspend fun getUserInfo() =
    withAuth {
      contentService.getUserInfo()
    }

  suspend fun getCurrentSlide() =
    withAuth {
      contentService.getCurrentSlide()
    }

  suspend fun makeChoice(
    fromPathName: String,
    fromTitle: String,
    slideChoice: SlideChoice,
    advance: Boolean,
  ): UserChoice =
    withAuth {
      contentService.makeChoice(fromPathName, fromTitle, slideChoice, advance)
    }

  suspend fun provideReason(
    fromPathName: String,
    fromTitle: String,
    slideChoice: SlideChoice,
    reason: String,
  ): SlideData =
    withAuth {
      contentService.provideReason(fromPathName, fromTitle, slideChoice, reason)
    }

  suspend fun goBackInTime(parentTitle: ParentTitle) =
    withAuth {
      contentService.goBackInTime(parentTitle)
    }
}
