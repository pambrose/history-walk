package com.github.pambrose

import io.kvision.state.ObservableList
import io.kvision.state.observableListOf
import io.kvision.utils.syncWithList
import kotlinx.coroutines.launch

object Model {

  private val addressService = AddressService()
  private val profileService = ProfileService()
  private val registerProfileService = RegisterProfileService()
  private val contentService = ContentService()

  val addresses: ObservableList<Address> = observableListOf()
  val profile: ObservableList<Profile> = observableListOf()

  var search: String? = null
    set(value) {
      field = value
      AppScope.launch {
        getAddressList()
      }
    }
  var types: String = "all"
    set(value) {
      field = value
      AppScope.launch {
        getAddressList()
      }
    }
  var sort = Sort.FN
    set(value) {
      field = value
      AppScope.launch {
        getAddressList()
      }
    }

  suspend fun getAddressList() {
    Security.withAuth {
      val newAddresses = addressService.getAddressList(search, types, sort)
      addresses.syncWithList(newAddresses)
    }
  }

  suspend fun addAddress(address: Address) {
    Security.withAuth {
      addressService.addAddress(address)
      getAddressList()
    }
  }

  suspend fun updateAddress(address: Address) {
    Security.withAuth {
      addressService.updateAddress(address)
      getAddressList()
    }
  }

  suspend fun deleteAddress(id: Int): Boolean {
    return Security.withAuth {
      val result = addressService.deleteAddress(id)
      getAddressList()
      result
    }
  }

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
