package com.pambrose.historywalk.auth

import com.pambrose.historywalk.Role

/**
 * The role rule: an address in `TEACHER_EMAILS` is a teacher, else one whose local part starts with two digits is a
 * student, else it is refused.
 */
object Roles {
  private val studentLocalPart = Regex("^\\d\\d")

  /** The comma-separated addresses in [raw] (`TEACHER_EMAILS`), trimmed and lowercased, blanks dropped. */
  fun parseTeacherEmails(raw: String?): Set<String> =
    raw
      .orEmpty()
      .split(",")
      .map { it.trim().lowercase() }
      .filter { it.isNotEmpty() }
      .toSet()

  /**
   * The role the rule gives [email], in any case, or null when it refuses the address. Does not check the domain; the
   * caller does. [teacherEmails] must already be lowercased.
   */
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
