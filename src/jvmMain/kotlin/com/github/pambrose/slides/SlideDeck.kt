package com.github.pambrose.slides

import io.github.oshai.kotlinlogging.KotlinLogging

class SlideDeck {
  private val slideList = mutableListOf<Slide>()
  private val slideMap = mutableMapOf<String, Slide>()
  val slideIdMap = mutableMapOf<Int, MutableList<Slide>>()
  lateinit var rootSlide: Slide
  lateinit var successSlide: Slide

  fun slide(
    id: Int,
    title: String,
    content: String,
    root: Boolean = false,
    success: Boolean = false,
    displayTitle: Boolean = true,
    block: Slide.() -> Unit = { },
  ) =
    Slide(id, title, content, root, success, this, displayTitle = displayTitle)
      .apply { block() }
      .also { slide -> addSlideToDeck(slide) }

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

  fun findSlideByPathName(pathName: String): Slide {
    val slide = if (pathName == ROOT) rootSlide else slideMap[pathName]

    return if (slide == null) {
      logger.error { "Invalid slide name: $pathName" }
      rootSlide
    } else {
      slide
    }
  }

  fun findSlideById(
    id: Int,
    version: Int = 0,
  ): Slide? = slideIdMap[id]?.let { if (version < it.size) it[version] else null }

  fun containsSlideByPathName(pathName: String) = slideMap.containsKey(pathName)

  fun addSlideToDeck(slide: Slide) {
    slideList += slide
  }

  // Each instance of the slide is kept in a list
  fun addSlideToIdMap(slide: Slide) {
    slideIdMap.computeIfAbsent(slide.id) { mutableListOf() } += slide
  }

  companion object {
    private val logger = KotlinLogging.logger {}
    const val ROOT = "/"

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