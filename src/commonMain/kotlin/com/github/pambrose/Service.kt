package com.github.pambrose

import io.kvision.annotations.KVService
import kotlinx.serialization.Serializable

enum class Sort {
  FN, LN, E, F
}

@KVService
interface IAddressService {
  suspend fun getAddressList(search: String?, types: String, sort: Sort): List<Address>
  suspend fun addAddress(address: Address): Address
  suspend fun updateAddress(address: Address): Address
  suspend fun deleteAddress(id: Int): Boolean
}

@KVService
interface IProfileService {
  suspend fun getProfile(): Profile
}

@KVService
interface IRegisterProfileService {
  suspend fun registerProfile(profile: Profile, password: String): Boolean
}

@KVService
interface IContentService {
  suspend fun lastSlide(title: String): SlideData
}

@KVService
interface IUserService {
  suspend fun choose(user: String, fromTitle: String, toTitle: String, choice: String, reason: String): String
}

@Serializable
class ChoiceTitle(val choice: String, val title: String)

@Serializable
enum class ChoiceOrientation { VERTICAL, HORIZONTAL }