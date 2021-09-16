package com.github.pambrose

object Model {

  private val registerProfileService = RegisterProfileService()
  private val contentService = ContentService()

  suspend fun registerProfile(profile: Profile, password: String): Boolean {
    return try {
      registerProfileService.registerProfile(profile, password)
    } catch (e: Exception) {
      console.log(e)
      false
    }
  }

  suspend fun refreshPanel(title: String) {
    Security.withAuth {
      try {
        val currentSlide = contentService.currentSlide(title)
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
        contentService.reason(fromTitle, abbrev, title, reason)
      } catch (e: Exception) {
        console.log(e)
        throw e
      }
    }
}
