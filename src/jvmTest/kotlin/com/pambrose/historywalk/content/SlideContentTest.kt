package com.pambrose.historywalk.content

import com.pambrose.historywalk.ParentTitle
import com.pambrose.historywalk.SlideChoice
import com.pambrose.historywalk.SlideData
import com.pambrose.historywalk.Utils.transformText
import com.pambrose.historywalk.slides.Slide
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

// Checks the real decks the way a student walks them: from the root slide, through every choice (DeckWalk.kt)
class SlideContentTest : StringSpec() {
  private val decks = listOf("Jermain Longuen" to jermainLonguenSlides, "Moses" to mosesSlides)

  init {
    decks.forEach { (name, deck) ->
      val slides = deck.reachableSlides()

      "$name: every slide a student can reach is found again by its path name" {
        slides.filterNot { deck.findSlideByPathName(it.pathName) === it }.map { it.pathName }.shouldBeEmpty()
      }

      "$name: every choice leads to a slide whose parent is the slide it was chosen from" {
        slides
          .flatMap { slide ->
            slide.forwardChoices().filter { it.parentSlide !== slide }.map { "${slide.pathName} -> ${it.title}" }
          }.shouldBeEmpty()
      }

      // The deck's successSlide may be a template that is reached only through copies, and the summary page counts
      // the students who reached a slide with its title, so every reachable success slide must carry that title
      "$name: a success slide can be reached and every one has the deck's success title" {
        val successTitles = slides.filter { it.success }.map { it.title }.toSet()
        successTitles shouldBe setOf(deck.successSlide.title)
      }

      "$name: every go-back choice lands on one of the slide's ancestors" {
        slides
          .flatMap { slide ->
            slide.goBacks().filter { (data, choice) -> runCatching { data.goBackTarget(choice) }.isFailure }
          }.map { (data, choice) ->
            "${data.pathName} goes back ${choice.offset} with ${data.parentTitles.size} ancestors"
          }.shouldBeEmpty()
      }

      "$name: every image a slide shows is in the jar" {
        val images = slides.flatMap { imagePattern.findAll(it.content).map { match -> match.groupValues[1] } }.toSet()
        images.filter { javaClass.getResource("/static/images/$it") == null }.shouldBeEmpty()
      }

      "$name: every slide renders to HTML" {
        slides
          .filter { slide -> slide.content.transformText().let { it.isBlank() || "---" in it } }
          .map { it.pathName }
          .shouldBeEmpty()
      }
    }

    // Each of the walk's go-backs is offered as trying another building, so each must land on the nearest town above
    // it ("Third Town"), not on a building or on a road named for a town ("Main Road out of First Town")
    "Jermain Longuen: every go-back returns to the town the student is in" {
      val town = Regex("""^\w+ Town$""")
      jermainLonguenSlides
        .reachableSlides()
        .flatMap { slide -> slide.goBacks() }
        .filter { (data, choice) ->
          data.goBackTarget(choice) != data.parentTitles.lastOrNull { town.matches(it.title) }
        }.map { (data, choice) ->
          "${data.title}: \"${choice.choiceText}\" lands on ${data.goBackTarget(choice).title}"
        }.distinct()
        .shouldBeEmpty()
    }
  }

  companion object {
    private val imagePattern = Regex("""/static/images/([^"'\s)]+)""")

    // The slide as the client gets it, with only what a go-back reads: its ancestors, root first, and its choices
    private fun Slide.toSlideData() =
      SlideData(
        pathName = pathName,
        title = title,
        content = "",
        success = success,
        choices = choices.map { (text, to) -> SlideChoice(text, to.pathName, to.title, false, to.offset, false) },
        verticalChoices = verticalChoices,
        parentTitles = generateSequence(parentSlide) {
          it.parentSlide
        }.map { ParentTitle(it.pathName, it.title) }.toList().reversed(),
        offset = offset,
        displayTitle = displayTitle,
        decisionCount = 0,
        displayConsecutiveCorrectDecisions = false,
        consecutiveCorrectDecisions = 0,
        showResetButton = false,
      )

    private fun Slide.goBacks() =
      toSlideData().let { data -> data.choices.filter { it.offset != 0 }.map { data to it } }
  }
}
