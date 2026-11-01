package com.pambrose.historywalk

import com.pambrose.historywalk.Utils.toUuid
import com.pambrose.historywalk.Utils.transformText
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.util.UUID

class UtilsTest : StringSpec() {
  init {
    "markdown is rendered to HTML" {
      "Some **bold** and _italic_ text".transformText().run {
        this shouldContain "<strong>bold</strong>"
        this shouldContain "<em>italic</em>"
      }
    }

    "HTML in the content is passed through, not escaped" {
      "<div style=\"text-align:center;\">Hello</div>".transformText().run {
        this shouldContain "<div style="
        this shouldContain ">Hello</div>"
        this shouldNotContain "&lt;"
        this shouldNotContain "---"
      }
    }

    "double quotes in the text are kept" {
      "He whispered \"run\" and fled".transformText() shouldContain "He whispered \"run\" and fled"
    }

    "single quotes in the text are kept" {
      "Jermain's horse and the 'patrol'".transformText() shouldContain "Jermain's horse and the 'patrol'"
    }

    "an HTML attribute keeps its double quotes" {
      """<img src="/static/images/pistol.jpg" alt="Pic"/>""".transformText() shouldContain
        """<img src="/static/images/pistol.jpg" alt="Pic"/>"""
    }

    "markdown is rendered with the common indent removed" {
      MarkdownParser
        .toHtml(
        """
          # Title
          Body text
        """,
      ).run {
        this shouldContain "<h1>Title</h1>"
        this shouldContain "Body text"
        this shouldNotContain "<pre>"
      }
    }

    "a UUID string is parsed" {
      val uuid = UUID.randomUUID()
      uuid.toString().toUuid() shouldBe uuid
    }

    "a malformed UUID string is refused" {
      shouldThrow<IllegalArgumentException> { "not-a-uuid".toUuid() }
    }
  }
}
