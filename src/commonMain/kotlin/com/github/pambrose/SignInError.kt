package com.github.pambrose

/** Why a Google sign-in was refused. The [code] travels in the `/?error=` redirect; [message] is what the page shows. */
enum class SignInError(
  val code: String,
) {
  FAILED("failed"),
  UNVERIFIED("unverified"),
  WRONG_DOMAIN("domain"),
  NOT_ALLOWED("not-allowed"),
  ;

  fun message(email: String?): String {
    val account = email ?: "That account"
    return when (this) {
      FAILED -> "Sign-in didn't complete. Please try again."
      UNVERIFIED -> "Google hasn't verified the email address on this account. Please use your school account."
      WRONG_DOMAIN -> "$account isn't an ${School.DEFAULT_DOMAIN} account. Please sign in with your school account."
      NOT_ALLOWED -> "$account isn't set up to use History Walk. Please ask your teacher if you need access."
    }
  }

  companion object {
    const val ERROR_PARAM = "error"
    const val EMAIL_PARAM = "email"

    fun fromCode(code: String?): SignInError? = entries.firstOrNull { it.code == code }
  }
}
