package com.github.pambrose.slides

import io.github.oshai.kotlinlogging.KotlinLogging

class Slide(
  val id: Int,
  val title: String,
  val content: String,
  val root: Boolean = false,
  val success: Boolean = false,
  val slideDeck: SlideDeck,
  val offset: Int = 0,
  val displayTitle: Boolean = true,
) {
  var verticalChoices = true
  val choices = mutableMapOf<String, Slide>()

  // Assigned when the slideMap is built from the slideList
  var parentSlide: Slide? = null

  val hasChoices get() = choices.isNotEmpty()
  val isSubTree: Boolean get() = parentSlide?.isSubTree ?: !root
  val pathName: String get() = "${parentSlide?.pathName ?: ""}/$title"

  init {
    if (offset == 0)
      require(title.isNotEmpty()) { "Slide title cannot be empty" }

    slideDeck.addSlideToIdMap(this)
  }

  fun copyOf(title: String = this.title, displayTitle: Boolean = this.displayTitle): Slide =
    Slide(id, title, content, root, success, slideDeck, offset, displayTitle)
      .also { copy ->
        copy.verticalChoices = verticalChoices
        choices.forEach { (text, slide) -> copy.choices[text] = slide.copyOf().also { it.parentSlide = copy } }
        slideDeck.addSlideToDeck(copy)
      }

  fun verticalChoices() {
    verticalChoices = true
  }

  fun horizontalChoices() {
    verticalChoices = false
  }

  fun choice(text: String, slide: Slide) {
    require(text.isNotEmpty()) { "Choice text cannot be empty for ${slide.title}" }
    choices[text] = slide
    slide.parentSlide = this
  }

  fun validateSlide() {
    fun <T> Collection<T>.dups() = groupingBy { it }.eachCount().filter { it.value > 1 }.map { it.key }

    choices.map { it.key }.dups()
      .also { dups ->
        if (dups.isNotEmpty())
          error("""Slide "$title" has duplicate choice titles: $dups""")
      }

    // Ignore children that are dead ends, e.g., "Incorrect Choice" slide
    choices.map { it.value.title }.dups()
      .also { dups ->
        if (dups.isNotEmpty())
          error("""Slide "$title" has duplicate choice slide titles: $dups""")
      }
  }

  override fun toString() = "Slide(id='$id', title='$title', choices=${choices.size}, parentSlide=$parentSlide)"

  companion object {
    private val logger = KotlinLogging.logger {}
  }
}