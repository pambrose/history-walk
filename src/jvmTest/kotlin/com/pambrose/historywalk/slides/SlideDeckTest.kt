package com.pambrose.historywalk.slides

import com.pambrose.historywalk.slides.SlideDeck.Companion.ROOT
import com.pambrose.historywalk.slides.SlideDeck.Companion.slideDeck
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs

class SlideDeckTest : StringSpec() {
  // Start -> Win (success) or Lose (dead end); Orphan is never chosen
  private fun simpleDeck() =
    slideDeck {
      val win = slide(id = 3, title = "Win", content = "You won", success = true)
      val lose = slide(id = 2, title = "Lose", content = "Wrong way")
      slide(id = 7, title = "Orphan", content = "Never chosen")
      slide(id = 1, title = "Start", content = "Begin here", root = true) {
        choice("Go right", win)
        choice("Go wrong", lose)
      }
    }

  init {
    "the root and success slides are found" {
      val deck = simpleDeck()
      deck.rootSlide.title shouldBe "Start"
      deck.successSlide.title shouldBe "Win"
    }

    "a path name is the chain of titles from the root" {
      val deck = simpleDeck()
      deck.rootSlide.pathName shouldBe "/Start"
      deck.rootSlide.choices
        .getValue("Go right")
        .pathName shouldBe "/Start/Win"
      deck.rootSlide.choices
        .getValue("Go wrong")
        .pathName shouldBe "/Start/Lose"
    }

    "a slide is found by its path name" {
      val deck = simpleDeck()
      deck.findSlideByPathName("/Start/Win") shouldBeSameInstanceAs deck.successSlide
      deck.containsSlideByPathName("/Start/Lose").shouldBeTrue()
    }

    "the root path name finds the root slide" {
      val deck = simpleDeck()
      deck.findSlideByPathName(ROOT) shouldBeSameInstanceAs deck.rootSlide
    }

    "an unknown path name falls back to the root slide" {
      val deck = simpleDeck()
      deck.containsSlideByPathName("/Start/Renamed").shouldBeFalse()
      deck.findSlideByPathName("/Start/Renamed") shouldBeSameInstanceAs deck.rootSlide
    }

    "a slide that no choice leads to is left out of the deck" {
      val deck = simpleDeck()
      deck.containsSlideByPathName("/Orphan").shouldBeFalse()
      deck.findSlideById(7).shouldBeNull()
    }

    "a slide is found by its id" {
      val deck = simpleDeck()
      deck.findSlideById(1) shouldBeSameInstanceAs deck.rootSlide
      deck.findSlideById(1, version = 1).shouldBeNull()
      deck.findSlideById(42).shouldBeNull()
    }

    "the success slide is not a dead end and a slide without choices is" {
      val deck = simpleDeck()
      deck.successSlide.hasChoices.shouldBeFalse()
      deck.findSlideByPathName("/Start/Lose").run { hasChoices || success }.shouldBeFalse()
      deck.rootSlide.hasChoices.shouldBeTrue()
    }

    "choices are vertical unless a slide asks for horizontal ones" {
      val deck =
        slideDeck {
          val win = slide(id = 2, title = "Win", content = "w", success = true)
          slide(id = 1, title = "Start", content = "s", root = true) {
            horizontalChoices()
            choice("win", win)
          }
        }
      deck.rootSlide.verticalChoices.shouldBeFalse()
      deck.successSlide.verticalChoices.shouldBeTrue()
    }

    "a copy gets its own title and its own copies of the choices below it" {
      val deck =
        slideDeck {
          val win = slide(id = 3, title = "Win", content = "w", success = true)
          val hub =
            slide(id = 2, title = "Hub", content = "h") {
              horizontalChoices()
              choice("finish", win)
            }
          slide(id = 1, title = "Start", content = "s", root = true) {
            choice("first", hub)
            choice("second", hub.copyOf("Hub again", displayTitle = false))
          }
        }

      val hub = deck.findSlideByPathName("/Start/Hub")
      val copy = deck.findSlideByPathName("/Start/Hub again")
      copy shouldNotBeSameInstanceAs hub
      copy.id shouldBe hub.id
      copy.content shouldBe hub.content
      copy.displayTitle.shouldBeFalse()
      copy.verticalChoices.shouldBeFalse()

      val copiedWin = deck.findSlideByPathName("/Start/Hub again/Win")
      copiedWin shouldNotBeSameInstanceAs deck.findSlideByPathName("/Start/Hub/Win")
      copiedWin.parentSlide shouldBeSameInstanceAs copy
      copiedWin.success.shouldBeTrue()

      // Every instance of an id in the main tree is kept, in the order they were made
      deck.findSlideById(2) shouldBeSameInstanceAs hub
      deck.findSlideById(2, version = 1) shouldBeSameInstanceAs copy
    }

    "the first success slide is the deck's success slide when there are several" {
      val deck =
        slideDeck {
          val first = slide(id = 2, title = "First", content = "f", success = true)
          val second = slide(id = 3, title = "Second", content = "s", success = true)
          slide(id = 1, title = "Start", content = "s", root = true) {
            choice("a", first)
            choice("b", second)
          }
        }
      deck.successSlide.title shouldBe "First"
    }

    "a go-back slide carries its offset and has no title" {
      slideDeck {
        val win = slide(id = 2, title = "Win", content = "w", success = true)
        slide(id = 1, title = "Start", content = "s", root = true) {
          choice("win", win)
          choice("back", goBack(-2))
        }
      }.rootSlide.choices.getValue("back").run {
        offset shouldBe -2
        title shouldBe ""
      }
    }

    "a go-back offset cannot be zero" {
      shouldThrow<IllegalArgumentException> {
        slideDeck { goBack(0) }
      }.message shouldContain "Offset cannot be 0"
    }

    "a slide title cannot be empty" {
      shouldThrow<IllegalArgumentException> {
        slideDeck { slide(id = 1, title = "", content = "c", root = true) }
      }.message shouldContain "title cannot be empty"
    }

    "a choice text cannot be empty" {
      shouldThrow<IllegalArgumentException> {
        slideDeck {
          val win = slide(id = 2, title = "Win", content = "w", success = true)
          slide(id = 1, title = "Start", content = "s", root = true) { choice("", win) }
        }
      }.message shouldContain "Choice text cannot be empty"
    }

    "a deck without a root slide is refused" {
      shouldThrow<IllegalStateException> {
        slideDeck { slide(id = 1, title = "Win", content = "w", success = true) }
      }.message shouldContain "Missing a top-level slide"
    }

    "a deck with two root slides is refused" {
      shouldThrow<IllegalStateException> {
        slideDeck {
          val win = slide(id = 3, title = "Win", content = "w", success = true)
          slide(id = 1, title = "Start", content = "s", root = true) { choice("win", win) }
          slide(id = 2, title = "Other start", content = "o", root = true)
        }
      }.message shouldContain "Multiple top-level slides"
    }

    "a deck without a success slide is refused" {
      shouldThrow<IllegalStateException> {
        slideDeck {
          val lose = slide(id = 2, title = "Lose", content = "l")
          slide(id = 1, title = "Start", content = "s", root = true) { choice("lose", lose) }
        }
      }.message shouldContain "Missing a success slide"
    }

    "a success slide cannot have choices" {
      shouldThrow<IllegalStateException> {
        slideDeck {
          val lose = slide(id = 3, title = "Lose", content = "l")
          val win = slide(id = 2, title = "Win", content = "w", success = true) { choice("lose", lose) }
          slide(id = 1, title = "Start", content = "s", root = true) { choice("win", win) }
        }
      }.message shouldContain "cannot be a success slide and have choices"
    }

    "two choices of a slide cannot lead to slides with the same title" {
      shouldThrow<IllegalStateException> {
        slideDeck {
          val win = slide(id = 2, title = "Same", content = "w", success = true)
          val lose = slide(id = 3, title = "Same", content = "l")
          slide(id = 1, title = "Start", content = "s", root = true) {
            choice("a", win)
            choice("b", lose)
          }
        }
      }.message shouldContain "duplicate choice slide titles"
    }
  }
}
