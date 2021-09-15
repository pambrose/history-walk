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

  suspend fun currentSlide(title: String) =
    Security.withAuth {
      contentService.currentSlide(title)
    }

  suspend fun choose(fromTitle: String, choiceTitle: String, choice: String) =
    Security.withAuth {
      contentService.choose(fromTitle, choice, choiceTitle)
    }

  suspend fun reason(choiceId: String, reason: String) =
    Security.withAuth {
      contentService.reason(choiceId, reason)
    }
}
