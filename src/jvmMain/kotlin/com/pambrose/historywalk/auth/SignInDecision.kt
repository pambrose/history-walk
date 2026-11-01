package com.pambrose.historywalk.auth

import com.pambrose.historywalk.Role
import com.pambrose.historywalk.SignInError
import com.pambrose.historywalk.SignInError.Companion.EMAIL_PARAM
import com.pambrose.historywalk.SignInError.Companion.ERROR_PARAM
import com.pambrose.historywalk.auth.Roles.roleFor
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URLEncoder

/** Subset of the Google OAuth2 userinfo response used for sign-in. */
@Serializable
data class GoogleUserInfo(
  val id: String,
  val email: String,
  val name: String = "",
  val picture: String? = null,
  @SerialName("verified_email") val verifiedEmail: Boolean? = null,
  val hd: String? = null,
)

/** The outcome of [signInDecision]. */
sealed interface SignInDecision {
  /** Google vouches for [email], the school manages it, and the role rule gave it a [role]. */
  data class Admitted(
    val email: String,
    val name: String,
    val picture: String?,
    val role: Role,
  ) : SignInDecision

  /** Sign-in refused for [error]; [email] is the address involved, if known. */
  data class Rejected(
    val error: SignInError,
    val email: String?,
  ) : SignInDecision
}

/**
 * Whether this Google account may sign in. Checked in order: Google verified the address; the address ends with
 * `@domain` and the Workspace hosted domain `hd` (which the browser cannot drop, unlike the `hd` hint sent with the
 * sign-in) is [domain]; and the role rule admits the address.
 */
fun GoogleUserInfo.signInDecision(
  domain: String,
  teacherEmails: Set<String>,
): SignInDecision {
  val address = email.lowercase()
  if (verifiedEmail != true) return SignInDecision.Rejected(SignInError.UNVERIFIED, email)
  if (!address.endsWith("@${domain.lowercase()}") || !hd.equals(domain, ignoreCase = true))
    return SignInDecision.Rejected(SignInError.WRONG_DOMAIN, email)
  val role = roleFor(address, teacherEmails) ?: return SignInDecision.Rejected(SignInError.NOT_ALLOWED, email)
  return SignInDecision.Admitted(address, name, picture, role)
}

/** The landing-page URL that reports this refusal. */
fun SignInDecision.Rejected.redirectUrl(): String =
  "/?$ERROR_PARAM=${error.code}" +
    (email?.let { "&$EMAIL_PARAM=${URLEncoder.encode(it, Charsets.UTF_8)}" } ?: "")
