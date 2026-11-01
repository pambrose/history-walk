package com.pambrose.historywalk.auth

import com.pambrose.historywalk.Role

object Roles {
  private val studentLocalPart = Regex("^\\d\\d")

  fun parseTeacherEmails(raw: String?): Set<String> =
    raw.orEmpty()
      .split(",")
      .map { it.trim().lowercase() }
      .filter { it.isNotEmpty() }
      .toSet()

  // Does not check the domain; the caller does. teacherEmails must already be lowercased.
  fun roleFor(
    email: String,
    teacherEmails: Set<String>,
  ): Role? {
    val normalized = email.trim().lowercase()
    return when {
      normalized in teacherEmails -> Role.TEACHER
      studentLocalPart.containsMatchIn(normalized.substringBefore('@')) -> Role.STUDENT
      else -> null
    }
  }
}
