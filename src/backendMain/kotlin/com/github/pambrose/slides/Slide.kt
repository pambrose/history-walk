package com.github.pambrose.slides

class Slide(val title: String, private val slideContent: SlideContent) {
  var parentSlide: Slide? = null
  var content: String = ""
  var success = false
  var verticalChoices = true
  val choices = mutableMapOf<String, String>()

  val hasChoices: Boolean
    get() = choices.isNotEmpty()

  init {
    require(title !in slideContent.allSlides.keys) { "Slide titles must be unique: $title" }
    slideContent.allSlides[title] = this
  }

  fun verticalChoices() {
    verticalChoices = true
  }

  fun horizontalChoices() {
    verticalChoices = false
  }

  fun choice(choice: String, destination: String) {
    choices[choice] = destination
  }
}
