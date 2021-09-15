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

  suspend fun hello() {
    Security.withAuth {
      console.log("Before hello()")
      val answer = contentService.hello()
      console.log("After hello() = $answer")
    }
  }

  suspend fun currentSlide(title: String) {
    Security.withAuth {
      console.log("Before currentSlide()")
      val currentSlide = contentService.currentSlide(title)
      console.log("After currentSlide() = ${currentSlide.title}")

      MainPanel.panel.displaySlide(currentSlide)
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
      contentService.reason(choiceId, reason)
    }
}
