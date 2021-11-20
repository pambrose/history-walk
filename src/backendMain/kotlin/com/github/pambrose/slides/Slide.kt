package com.github.pambrose.slides

import com.github.pambrose.ChoiceOrientation
import mu.KLogging

class Slide(val title: String, private val slideContent: SlideContent) {
  var parentSlide: Slide? = null
  var content: String = ""
  var success = false
  val choices = mutableMapOf<String, String>()
  var choiceOrientation: ChoiceOrientation = ChoiceOrientation.VERTICAL

  val hasChoices: Boolean
    get() = choices.isNotEmpty()

  init {
    require(title !in slideContent.allSlides.keys) { "Slide titles must be unique: $title" }
    slideContent.allSlides[title] = this
  }

  fun vertical() {
    choiceOrientation = ChoiceOrientation.VERTICAL
  }

  fun horizontal() {
    choiceOrientation = ChoiceOrientation.HORIZONTAL
  }

  fun choice(choice: String, destination: String) {
    choices[choice] = destination
  }

  companion object : KLogging()
}