package com.github.pambrose

import com.github.pambrose.ClientUtils.withAuth

object Rpc {

  private val registerUserService = RegisterUserService()
  private val contentService = ContentService()

  suspend fun registerUser(registerData: RegisterData) =
    try {
      registerUserService.registerUser(registerData)
    } catch (e: Exception) {
      console.log(e)
      false
    }

  suspend fun getCurrentSlide() =
    withAuth {
      contentService.getCurrentSlide()
    }

  suspend fun makeChoice(fromPathName: String, fromTitle: String, slideChoice: SlideChoice, advance: Boolean) =
    withAuth {
      contentService.makeChoice(fromPathName, fromTitle, slideChoice, advance)
    }

  suspend fun provideReason(fromPathName: String, fromTitle: String, slideChoice: SlideChoice, reason: String) =
    withAuth {
      contentService.provideReason(fromPathName, fromTitle, slideChoice, reason)
    }

  suspend fun goBackInTime(parentTitle: ParentTitle) =
    withAuth {
      contentService.goBackInTime(parentTitle)
    }
}
