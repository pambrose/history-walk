package com.github.pambrose

import io.kvision.state.ObservableList
import io.kvision.state.ObservableValue
import io.kvision.state.observableListOf
import io.kvision.utils.syncWithList
import kotlinx.coroutines.launch

object Model {

  private val addressService = AddressService()
  private val profileService = ProfileService()
  private val registerProfileService = RegisterProfileService()
  private val contentService = ContentService()
  private val userService = UserService()

  val addresses: ObservableList<Address> = observableListOf()
  val profile: ObservableList<Profile> = observableListOf(Profile())

  val obsTitle = ObservableValue("")
  val obsContent = ObservableValue("")
  val obsChoices: ObservableList<ChoiceTitle> = observableListOf()

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

  suspend fun lastSlide(title: String) = contentService.lastSlide(title)

  suspend fun choose(user: String, fromTitle: String, toTitle: String, choice: String, reason: String) =
    userService.choose(user, fromTitle, toTitle, choice, reason)
}
