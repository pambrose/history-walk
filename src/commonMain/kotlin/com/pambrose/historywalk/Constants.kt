package com.pambrose.historywalk

object Auth {
  const val AUTH_COOKIE = "HistoryWalkAuth"

  /** The sign-in dialog's title when LOGIN_GREETING is unset. */
  const val DEFAULT_LOGIN_GREETING = "History Walk"
}

object School {
  const val DEFAULT_DOMAIN = "athenian.org"
}

/** The documentation site (website/history-walk, published to GitHub Pages). */
object Docs {
  /** The teacher guide, which a teacher's help link opens. */
  const val TEACHER_GUIDE = "https://pambrose.github.io/history-walk/teachers/"
}

object EndPoints {
  const val LOGOUT = "logout"
  const val AUTH_LOGIN = "auth/login"
  const val AUTH_CALLBACK = "auth/callback"
  const val AUTH_GREETING = "auth/greeting"
  const val AUTH_DOMAIN = "auth/domain"
  const val USER_RESET = "userreset"
  const val SUMMARY = "summary"
  const val REASONS = "reasons"
  const val SLIDE = "slide"
  const val ADMIN = "admin"
  const val ADMIN_VIEW = "admin/view"
  const val ADMIN_CLEAR = "admin/clear"
}
