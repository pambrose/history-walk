package com.github.pambrose

import io.kvision.state.ObservableList
import io.kvision.state.observableListOf

object Model {

  private val profileService = ProfileService()
  private val registerProfileService = RegisterProfileService()
  private val contentService = ContentService()

  val profile: ObservableList<Profile> = observableListOf()

  suspend fun readProfile() {
    Security.withAuth {
      profile[0] = profileService.getProfile()
    }
  }

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

  suspend fun choose(fromTitle: String, choiceTitle: String, choice: String) =
    Security.withAuth {
      try {
        contentService.choose(fromTitle, choice, choiceTitle)
      } catch (e: Exception) {
        console.log(e)
        throw e
      }
    }

  suspend fun reason(choiceId: String, reason: String) =
    Security.withAuth {
      try {
        contentService.reason(choiceId, reason)
      } catch (e: Exception) {
        console.log(e)
        throw e
      }
    }
}
