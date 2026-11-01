package com.pambrose.historywalk

object Auth {
  const val AUTH_COOKIE = "HistoryWalkAuth"

  /** The sign-in dialog's title when LOGIN_GREETING is unset. */
  const val DEFAULT_LOGIN_GREETING = "History Walk"
}

object School {
  const val DEFAULT_DOMAIN = "athenian.org"
}

object EndPoints {
  const val LOGOUT = "logout"
  const val AUTH_LOGIN = "auth/login"
  const val AUTH_CALLBACK = "auth/callback"
  const val AUTH_GREETING = "auth/greeting"
  const val USER_RESET = "userreset"
  const val CONTENT_RESET = "contentreset"
  const val SUMMARY = "summary"
  const val REASONS = "reasons"
  const val SLIDE = "slide"
}
