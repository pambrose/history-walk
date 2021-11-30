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

  suspend fun makeChoice(fromTitle: String, abbrev: String, toTitle: String, advance: Boolean) =
    withAuth {
      contentService.makeChoice(fromTitle, abbrev, toTitle, advance)
    }

  suspend fun provideReason(fromTitle: String, abbrev: String, title: String, reason: String) =
    withAuth {
      contentService.provideReason(fromTitle, abbrev, title, reason)
    }

  suspend fun goBackInTime(title: String) =
    withAuth {
      contentService.goBackInTime(title)
    }
}
