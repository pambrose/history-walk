package com.pambrose.historywalk

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class SlideDataTest : StringSpec() {
  private val town = ParentTitle("/Start/Town", "Town")
  private val mansion = ParentTitle("/Start/Town/Mansion", "Mansion")

  // /Start/Town/Mansion/Bluff, with go-back choices of the given offsets
  private fun bluff(vararg offsets: Int) =
    SlideData(
      pathName = "/Start/Town/Mansion/Bluff",
      title = "Bluff",
      content = "",
      success = false,
      choices = offsets.map { SlideChoice("back $it", "/Start/Town/Mansion/Bluff/", "", true, it, false) },
      verticalChoices = true,
      parentTitles = listOf(ParentTitle("/Start", "Start"), town, mansion),
      offset = 0,
      displayTitle = true,
      decisionCount = 0,
      displayConsecutiveCorrectDecisions = false,
      consecutiveCorrectDecisions = 0,
      showResetButton = false,
    )

  init {
    "going back 1 returns to the slide's parent" {
      bluff(-1).run { goBackTarget(choices.single()) } shouldBe mansion
    }

    "going back 2 returns to the slide's grandparent" {
      bluff(-2).run { goBackTarget(choices.single()) } shouldBe town
    }

    "going back as far as the root returns to the root" {
      bluff(-3).run { goBackTarget(choices.single()).title } shouldBe "Start"
    }

    "going back past the root is refused" {
      shouldThrow<IllegalArgumentException> { bluff(-4).run { goBackTarget(choices.single()) } }
    }

    "a choice that does not go back has no go-back target" {
      shouldThrow<IllegalArgumentException> { bluff(0).run { goBackTarget(choices.single()) } }
    }
  }
}
