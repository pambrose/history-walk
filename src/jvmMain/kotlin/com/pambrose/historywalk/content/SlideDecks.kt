package com.pambrose.historywalk.content

import com.pambrose.historywalk.slides.SlideDeck

/** The deck a server serves when SLIDES_VARIABLE_NAME is unset or blank. */
const val DEFAULT_SLIDE_DECK = "jermain"

// The decks by the SLIDES_VARIABLE_NAME value that selects each. Each deck is a top-level val built when its file is
// first read, so a server builds only the deck it serves.
private val slideDecks: Map<String, () -> SlideDeck> =
  linkedMapOf(
    "jermain" to { jermainLonguenSlides },
    "moses" to { mosesSlides },
  )

/**
 * The name of the deck [value] (SLIDES_VARIABLE_NAME) selects: `jermain` or `moses`, trimmed and in any case, and
 * [DEFAULT_SLIDE_DECK] when it is unset or blank. Any other value fails, so a typo or a stale value stops the server at
 * startup, as a missing sign-in setting does, rather than serve the other walk.
 */
fun slideDeckName(value: String?): String {
  val name =
    value
      .orEmpty()
      .trim()
      .lowercase()
      .ifEmpty { DEFAULT_SLIDE_DECK }
  require(name in slideDecks) {
    "SLIDES_VARIABLE_NAME is \"$value\", which names no slide deck: set it to " +
      "${slideDecks.keys.joinToString(" or ")}, or leave it unset for $DEFAULT_SLIDE_DECK"
  }
  return name
}

/** The deck [value] (SLIDES_VARIABLE_NAME) selects; see [slideDeckName] for the values it accepts. */
fun slideDeckFor(value: String?): SlideDeck = slideDecks.getValue(slideDeckName(value))()
