package com.pambrose.historywalk

/**
 * Up to two uppercase initials from the first and last words of [fullName]; when the name is blank,
 * the first letter of [email] uppercased.
 */
fun initialsOf(
  fullName: String,
  email: String,
): String {
  val words = fullName.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
  val letters =
    when {
      words.isEmpty() -> email.trim().take(1)
      words.size == 1 -> words.first().take(1)
      else -> words.first().take(1) + words.last().take(1)
    }
  return letters.uppercase()
}
