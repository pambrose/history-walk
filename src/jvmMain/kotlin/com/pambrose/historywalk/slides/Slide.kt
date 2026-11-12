package com.pambrose.historywalk.slides

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

  /** This slide offers no way on and is not the success slide. */
  val isDeadEnd: Boolean get() = !hasChoices && !success

  /**
   * A choice that leads here is a wrong turn: this slide is a dead end, or its one way on is a forward choice (a
   * "Continue") that leads to one. Jermain's Autumn and Spring Departures and the Ramshackle Hut (cont.) slides each
   * stand between a fatal decision and the Paddy Rollers ending, and a "Continue" is never recorded, so without this
   * the decision itself was stored as a correct one, and the consecutive-correct count never reset for it.
   */
  val leadsToDeadEnd: Boolean
    get() = isDeadEnd || choices.values.singleOrNull()?.let { it.offset == 0 && it.leadsToDeadEnd } == true

  /** The slides above this one, the root first. */
  val ancestors: List<Slide> get() = generateSequence(parentSlide) { it.parentSlide }.toList().asReversed()

  init {
    if (offset == 0)
      require(title.isNotEmpty()) { "Slide title cannot be empty" }

    slideDeck.addSlideToIdMap(this)
  }

  fun copyOf(
    title: String = this.title,
    displayTitle: Boolean = this.displayTitle,
  ): Slide =
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

  /**
   * Adds a choice. Choices are kept by their text, so a second choice with the same text would silently replace the
   * first (Moses's 14th decision lost one that way), and is refused instead.
   */
  fun choice(
    text: String,
    slide: Slide,
  ) {
    require(text.isNotEmpty()) { "Choice text cannot be empty for ${slide.title}" }
    require(text !in choices) { "Slide \"$title\" has two choices with the text \"$text\"" }
    choices[text] = slide
    slide.parentSlide = this
  }

  fun validateSlide() {
    fun <T> Collection<T>.dups() = groupingBy { it }.eachCount().filter { it.value > 1 }.map { it.key }

    // Two choices with the same text are refused as they are added (choice), since the second would replace the first

    // Two children with the same title would have the same path name; a deck gives each copy of a dead end its own
    // title instead (Moses's Incorrect1, Incorrect2, ...)
    choices
      .map { it.value.title }
      .dups()
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
