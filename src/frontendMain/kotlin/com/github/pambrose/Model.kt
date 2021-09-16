package com.github.pambrose

object Model {

  private val registerUserService = RegisterUserService()
  private val contentService = ContentService()

  suspend fun registerUser(registerData: RegisterData) =
    try {
      registerUserService.registerUser(registerData)
    } catch (e: Exception) {
      console.log(e)
      false
    }

  suspend fun refreshPanel() {
    Security.withAuth {
      try {
        val currentSlide = contentService.currentSlide()
        MainPanel.panel.displaySlide(currentSlide)
      } catch (e: Exception) {
        console.log(e)
        throw e
      }
    }
  }

  suspend fun choose(fromTitle: String, abbrev: String, title: String) =
    Security.withAuth {
      try {
        contentService.choose(fromTitle, abbrev, title)
      } catch (e: Exception) {
        console.log(e)
        throw e
      }
    }

  suspend fun reason(fromTitle: String, abbrev: String, title: String, reason: String) =
    Security.withAuth {
      try {
        val currentSlide = contentService.reason(fromTitle, abbrev, title, reason)
        MainPanel.panel.displaySlide(currentSlide)
      } catch (e: Exception) {
        console.log(e)
        throw e
      }
    }

  suspend fun goBack(title: String) =
    Security.withAuth {
      try {
        val currentSlide = contentService.goBack(title)
        MainPanel.panel.displaySlide(currentSlide)
      } catch (e: Exception) {
        console.log(e)
        throw e
      }
    }
}
