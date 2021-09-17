package com.github.pambrose

import com.github.pambrose.Security.withTryAuth

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

  suspend fun refreshPanel() =
    withTryAuth {
      contentService.getCurrentSlide()
    }

  suspend fun makeChoice(fromTitle: String, abbrev: String, title: String) =
    withTryAuth {
      contentService.makeChoice(fromTitle, abbrev, title)
    }

  suspend fun provideReason(fromTitle: String, abbrev: String, title: String, reason: String) =
    withTryAuth {
      contentService.provideReason(fromTitle, abbrev, title, reason)
    }

  suspend fun goBackInTime(title: String) =
    withTryAuth {
      contentService.goBackInTime(title)
    }
}
