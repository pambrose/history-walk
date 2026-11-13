package com.pambrose.historywalk.slides

import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * A walk's slides, built with [slideDeck]: the tree that grows from [rootSlide] by choices, with its [successSlide],
 * each slide found by its path name or by its id.
 */
class SlideDeck {
  private val slideList = mutableListOf<Slide>()
  private val slideMap = mutableMapOf<String, Slide>()
  val slideIdMap = mutableMapOf<Int, MutableList<Slide>>()
  lateinit var rootSlide: Slide
  lateinit var successSlide: Slide

  /**
   * Creates a slide in this deck and runs [block] on it, where its choices are added. The deck needs one [root] slide
   * and at least one [success] slide; with more than one, the first is the deck's [successSlide].
   */
  fun slide(
    id: Int,
    title: String,
    content: String,
    root: Boolean = false,
    success: Boolean = false,
    displayTitle: Boolean = true,
    block: Slide.() -> Unit = { },
  ) = Slide(id, title, content, root, success, this, displayTitle = displayTitle)
      .apply { block() }
      .also { slide -> addSlideToDeck(slide) }

  /**
   * The target of a go-back choice, which returns the student to the choosing slide's ancestor [offset] steps up (-1 is
   * its parent): a placeholder slide with id -1 and no title, which no path name finds. Refuses an [offset] of 0.
   */
  fun goBack(offset: Int): Slide {
    require(offset != 0) { "Offset cannot be 0" }
    return Slide(
      id = -1,
      title = "",
      content = "",
      root = false,
      success = false,
      slideDeck = this,
      offset = offset,
    )
  }

  private fun validateSlideDeck() {
    slideList.forEach { slide ->
      if (slide.success && slide.hasChoices)
        error("""Slide "${slide.pathName}" cannot be a success slide and have choices""")
    }

    rootSlide =
      slideList
        .filter { it.root }
        .let { rootSlides ->
          when {
            rootSlides.isEmpty() -> error("Missing a top-level slide")
            rootSlides.size > 1 -> error("Multiple top-level slides: ${rootSlides.map { it.title }}")
            else -> rootSlides.first()
          }
        }

    successSlide =
      slideList
        .filter { it.success }
        .let { successSlides ->
          with(successSlides) {
            when {
              isEmpty() -> error("Missing a success slide")
              size > 1 -> logger.warn { "${count()} success slides: ${map { it.title }}" }
            }
            first()
          }
        }
  }

  /**
   * The slide at [pathName], the root slide for [ROOT]. A path name no slide has (a renamed or moved slide's stored
   * progress) is logged as an error and gives the root slide.
   */
  fun findSlideByPathName(pathName: String): Slide {
    val slide = if (pathName == ROOT) rootSlide else slideMap[pathName]

    return if (slide == null) {
      logger.error { "Invalid slide name: $pathName" }
      rootSlide
    } else {
      slide
    }
  }

  /**
   * The slide with [id] in the deck's tree, or null when there is none. A slide and its copies share an id, and
   * [version] picks one, in the order they were made. For the teachers' `/slide/{id}/{version}` jump.
   */
  fun findSlideById(
    id: Int,
    version: Int = 0,
  ): Slide? = slideIdMap[id]?.let { if (version < it.size) it[version] else null }

  /** Whether a slide in the deck's tree is at [pathName], with no fallback to the root as [findSlideByPathName] has. */
  fun containsSlideByPathName(pathName: String) = slideMap.containsKey(pathName)

  /**
   * Adds [slide] to those [slideDeck] looks through once the deck is built, for its root and success slides and its
   * path names. Called for each slide made with `slide` and each copy ([Slide.copyOf]).
   */
  fun addSlideToDeck(slide: Slide) {
    slideList += slide
  }

  /** Adds [slide] to [slideIdMap] under its id; each instance of the slide is kept in a list. Each [Slide] calls it. */
  fun addSlideToIdMap(slide: Slide) {
    slideIdMap.computeIfAbsent(slide.id) { mutableListOf() } += slide
  }

  /** Holds [ROOT] and the [slideDeck] builder. */
  companion object {
    private val logger = KotlinLogging.logger {}
    const val ROOT = "/"

    /**
     * Builds a deck: runs [block] to make its slides, maps each slide in the tree under the root by its path name,
     * validates each ([Slide.validateSlide]), and drops the slides outside the tree. Fails on a deck with no root slide
     * or more than one, no success slide, or a success slide with choices.
     */
    fun slideDeck(block: SlideDeck.() -> Unit) =
      SlideDeck()
        .apply(block)
        .apply {
          slideList
            .filterNot { it.isSubTree }
            .forEach { slide ->
              logger.debug { "Added to map Title: ${slide.title} Path: '${slide.pathName}'" }
              slideMap[slide.pathName] = slide   // Built after all slides are added to get pathName right
              slide.validateSlide()
            }

          // Remove slides that are not in the main tree
          slideIdMap.values.forEach { slideList -> slideList.removeIf { it.isSubTree } }

          // Validate it
          validateSlideDeck()

          // Empty slideList
          slideList.clear()
        }
  }
}
