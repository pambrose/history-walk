package com.github.pambrose

import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.html.HtmlRenderer.HARD_BREAK
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet

object MarkdownParser {
  private val options by lazy { MutableDataSet().apply { set(HARD_BREAK, "<br />\n") } }
  private val parser by lazy { Parser.builder(options).build() }
  private val renderer by lazy { HtmlRenderer.builder(options).build() }

  fun toHtml(markdown: String) =
    synchronized(this) {
      parser.parse(markdown.trimIndent())
        .let {
          renderer.render(it)
        }
    }
}
