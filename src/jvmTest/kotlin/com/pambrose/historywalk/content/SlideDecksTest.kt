package com.pambrose.historywalk.content

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeSameInstanceAs

// SLIDES_VARIABLE_NAME picks the walk a server serves; a value that names no deck must stop the server, not fall
// through to the other walk
class SlideDecksTest : StringSpec() {
  init {
    "jermain selects the Jermain Longuen deck" {
      slideDeckFor("jermain") shouldBeSameInstanceAs jermainLonguenSlides
      slideDeckName("jermain") shouldBe "jermain"
    }

    "moses selects the Moses deck" {
      slideDeckFor("moses") shouldBeSameInstanceAs mosesSlides
      slideDeckName("moses") shouldBe "moses"
    }

    "an unset or blank value selects the default, the Jermain Longuen deck" {
      DEFAULT_SLIDE_DECK shouldBe "jermain"
      listOf(null, "", " ", "\t\n").forEach { value ->
        slideDeckFor(value) shouldBeSameInstanceAs jermainLonguenSlides
        slideDeckName(value) shouldBe DEFAULT_SLIDE_DECK
      }
    }

    "a value is read in any case, with surrounding whitespace ignored" {
      listOf("Jermain", "JERMAIN", "  jermain  ", "\tJerMain\n").forEach { value ->
        slideDeckFor(value) shouldBeSameInstanceAs jermainLonguenSlides
        slideDeckName(value) shouldBe "jermain"
      }
      listOf("Moses", "MOSES", " moses ", "\nmoSES\t").forEach { value ->
        slideDeckFor(value) shouldBeSameInstanceAs mosesSlides
        slideDeckName(value) shouldBe "moses"
      }
    }

    "a typo or a stale value is refused, naming the value and the accepted ones" {
      // A misspelling, the walk's full name, the decks' Kotlin names, and the default of the old scripted loader
      val refused = listOf("jermian", "Jermain Longuen", "jermainLonguenSlides", "mosesSlides", "slides", "jer main")
      refused.forEach { value ->
        val e = shouldThrow<IllegalArgumentException> { slideDeckFor(value) }
        e.message shouldContain "\"$value\""
        e.message shouldContain "jermain or moses"
        shouldThrow<IllegalArgumentException> { slideDeckName(value) }.message shouldBe e.message
      }
    }

    "the refusal message tells the operator what to set" {
      shouldThrow<IllegalArgumentException> { slideDeckFor(" Jermian ") }.message shouldBe
        "SLIDES_VARIABLE_NAME is \" Jermian \", which names no slide deck: set it to jermain or moses, " +
        "or leave it unset for jermain"
    }
  }
}
