package com.pambrose.historywalk

import io.kvision.core.Position
import io.kvision.html.Image
import io.kvision.utils.px
import io.kvision.utils.vh
import io.kvision.utils.vw

/**
 * A school photo behind the sign-in dialog, chosen at random each time the page loads (as in canvas-cache). It fills
 * the window and sits under the dialog's backdrop. The images are in src/jsMain/resources/img, served at /img/ by the
 * webpack dev server and, from the jar's assets/, by `Routes.staticContent`.
 */
object SignInBackground {
  private val images =
    listOf(
      "athenian-awe.jpg",
      "athenian-band.jpg",
      "athenian-mainhall1.jpg",
      "athenian-mainhall2.jpg",
      "athenian-overhead1.jpg",
      "athenian-overhead2.jpg",
      "athenian-sign.jpg",
      "athenian-walkway.jpg",
    )

  private var shown = false

  /** Shows a random photo behind the page's content, once; signing in leaves the page, which takes it away. */
  fun show() {
    if (shown) return
    shown = true
    // Decorative, so the alt is empty rather than missing: a missing alt makes screen readers announce the file name
    MainPanel.add(
      Image("/img/${images.random()}", alt = "") {
        position = Position.FIXED
        top = 0.px
        left = 0.px
        width = 100.vw
        height = 100.vh
        zIndex = 0
        setStyle("object-fit", "cover")
        setStyle("pointer-events", "none")
      },
    )
  }
}
