package com.pambrose.historywalk

/**
 * What a decision's reason must be. ContentService.provideReason enforces it; it lives in commonMain so the reason
 * dialog can share it. Pure Kotlin, so it compiles for the JVM and JS alike.
 */
object ReasonRule {
  /** The fewest words a reason may have. */
  const val MIN_WORDS = 5

  /**
   * The most characters a reason may have, leading and trailing whitespace aside. A reason is a sentence or two;
   * 2000 characters is roughly 300 words, far more than any honest answer needs, yet it keeps one call from storing,
   * and the teacher's reasons page from rendering, an unbounded text.
   */
  const val MAX_LENGTH = 2000

  /** The number of words in [text]: runs of characters between whitespace of any kind (tabs, newlines, NBSP, ...). */
  fun wordCount(text: String?): Int =
    text?.let { s -> s.indices.count { i -> !s[i].isWhitespace() && (i == 0 || s[i - 1].isWhitespace()) } } ?: 0

  /** Why [reason] is not acceptable, or null when it is. Leading and trailing whitespace are ignored. */
  fun problemWith(reason: String): String? {
    val text = reason.trim()
    return when {
      wordCount(text) < MIN_WORDS -> "A reason needs at least $MIN_WORDS words"
      text.length > MAX_LENGTH -> "A reason can be at most $MAX_LENGTH characters"
      else -> null
    }
  }
}
