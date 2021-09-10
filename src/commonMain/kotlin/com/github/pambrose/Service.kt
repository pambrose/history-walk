package com.github.pambrose

import io.kvision.annotations.KVService

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
  suspend fun currentSlide(title: String): SlideData
  suspend fun choose(fromTitle: String, choice: String, choiceTitle: String): ChoiceReason
  suspend fun reason(choiceId: String, reason: String): String
}