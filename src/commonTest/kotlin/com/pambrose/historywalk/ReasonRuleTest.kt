package com.pambrose.historywalk

import com.pambrose.historywalk.ReasonRule.MAX_LENGTH
import com.pambrose.historywalk.ReasonRule.MIN_WORDS
import com.pambrose.historywalk.ReasonRule.problemWith
import com.pambrose.historywalk.ReasonRule.wordCount
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class ReasonRuleTest : StringSpec() {
  init {
    "nothing has no words" {
      wordCount(null) shouldBe 0
      wordCount("") shouldBe 0
      wordCount(" \t\n ") shouldBe 0
    }

    "words are counted between spaces" {
      wordCount("one") shouldBe 1
      wordCount("I chose to go on") shouldBe 5
    }

    "any run of whitespace separates words, not just spaces and newlines" {
      wordCount("  a  b\tc\r\nd \u00A0e\u2003f  ") shouldBe 6
    }

    "punctuation stays part of its word" {
      wordCount("Well, it's safer, I think.") shouldBe 5
    }

    "a reason of fewer than five words is refused" {
      problemWith("only four words here") shouldBe "A reason needs at least $MIN_WORDS words"
      problemWith("one\ttwo\nthree four") shouldBe "A reason needs at least $MIN_WORDS words"
      problemWith("     ") shouldBe "A reason needs at least $MIN_WORDS words"
    }

    "a reason of five words is accepted" {
      problemWith("it looked like the safest way").shouldBeNull()
      problemWith("one two three four five").shouldBeNull()
    }

    "a reason up to the maximum length is accepted, and one character more is refused" {
      val atMax = "word ".repeat(MAX_LENGTH / 5).trimEnd().padEnd(MAX_LENGTH, 'x')
      atMax.length shouldBe MAX_LENGTH
      problemWith(atMax).shouldBeNull()
      problemWith(atMax + "x") shouldBe "A reason can be at most $MAX_LENGTH characters"
    }

    "leading and trailing whitespace do not count toward the length" {
      val atMax = "word ".repeat(MAX_LENGTH / 5).trimEnd().padEnd(MAX_LENGTH, 'x')
      problemWith("  \n$atMax\n  ").shouldBeNull()
    }
  }
}
