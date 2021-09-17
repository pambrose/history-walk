package com.github.pambrose

import com.github.pambrose.Security.withAuthAndTry

object RpcWrapper {

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
    withAuthAndTry {
      val currentSlide = contentService.currentSlide()
      MainPanel.panel.displaySlide(currentSlide)
    }

  suspend fun choose(fromTitle: String, abbrev: String, title: String) =
    withAuthAndTry {
      contentService.choose(fromTitle, abbrev, title)
    }

  suspend fun reason(fromTitle: String, abbrev: String, title: String, reason: String) =
    withAuthAndTry {
      val currentSlide = contentService.reason(fromTitle, abbrev, title, reason)
      MainPanel.panel.displaySlide(currentSlide)
    }

  suspend fun goBack(title: String) =
    withAuthAndTry {
      val currentSlide = contentService.goBack(title)
      MainPanel.panel.displaySlide(currentSlide)
    }
}
