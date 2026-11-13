package com.pambrose.historywalk

import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.html.HtmlRenderer.HARD_BREAK
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet

/** Renders slide content from markdown to HTML with flexmark, for `Utils.transformText`. */
object MarkdownParser {
  private val options by lazy { MutableDataSet().apply { set(HARD_BREAK, "<br />\n") } }
  private val parser by lazy { Parser.builder(options).build() }
  private val renderer by lazy { HtmlRenderer.builder(options).build() }

  /** [markdown], its common indent trimmed, as HTML, one call at a time, as the parser and renderer are shared. */
  fun toHtml(markdown: String) =
    synchronized(this) {
      parser
        .parse(markdown.trimIndent())
        .let {
          renderer.render(it)
        }
    }
}
