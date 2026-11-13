package com.pambrose.historywalk

import java.util.*

/** Helpers for slide content and user ids. */
object Utils {
  private const val LT_ESCAPE = "---LT---"
  private const val GT_ESCAPE = "---GT---"
  private const val DQUOTE_ESCAPE = "---DQ---"
  private const val SQUOTE_ESCAPE = "---SQ---"

  /**
   * This slide content rendered from markdown to HTML ([MarkdownParser]). The content's HTML and quotes are hidden from
   * the markdown renderer, which would otherwise escape them (an attribute's quotes to &quot;), and restored unchanged
   * in its output.
   */
  fun String.transformText(): String {
    val escaped = this
      .replace("<", LT_ESCAPE)
      .replace(">", GT_ESCAPE)
      .replace("\"", DQUOTE_ESCAPE)
      .replace("'", SQUOTE_ESCAPE)

    return MarkdownParser
      .toHtml(escaped)
      .replace(LT_ESCAPE, "<")
      .replace(GT_ESCAPE, ">")
      .replace(DQUOTE_ESCAPE, "\"")
      .replace(SQUOTE_ESCAPE, "'")
  }

  /** This string as a [UUID]; throws `IllegalArgumentException` when it is not one. */
  fun String.toUuid(): UUID = UUID.fromString(this)
}
