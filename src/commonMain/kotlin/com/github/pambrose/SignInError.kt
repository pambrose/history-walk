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

    private const val MAX_EMAIL_LENGTH = 254
    private val emailPattern = Regex("^[^\\s@\\u0000-\\u001F\\u007F]{1,64}@[^\\s@\\u0000-\\u001F\\u007F]{1,253}$")

    /**
     * The `email` from a refusal redirect, only if it looks like an address (it is shown in the sign-in prompt, and
     * anyone can craft the URL); otherwise null, so the generic message is shown.
     */
    fun refusalEmailOrNull(raw: String?): String? =
      raw?.takeIf { it.length <= MAX_EMAIL_LENGTH && emailPattern.matches(it) }
  }
}
