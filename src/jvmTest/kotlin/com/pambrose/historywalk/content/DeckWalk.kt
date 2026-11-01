package com.pambrose.historywalk.content

import com.pambrose.historywalk.slides.Slide
import com.pambrose.historywalk.slides.SlideDeck
import java.util.IdentityHashMap

// The walk a student can take through a real deck, shared by SlideContentTest and SlidePathsTest

/** The choices that lead on to a slide; a go-back (a non-zero offset) only returns to an ancestor. */
internal fun Slide.forwardChoices() = choices.values.filter { it.offset == 0 }

/**
 * Every slide a student can reach from the root through forward choices. Each slide object is visited once, so a slide
 * shared between two parents cannot loop the walk.
 */
internal fun SlideDeck.reachableSlides(): List<Slide> {
  val seen = IdentityHashMap<Slide, Unit>()
  val queue = ArrayDeque(listOf(rootSlide))
  while (queue.isNotEmpty()) {
    val slide = queue.removeFirst()
    if (seen.put(slide, Unit) == null) queue += slide.forwardChoices()
  }
  return seen.keys.toList()
}
