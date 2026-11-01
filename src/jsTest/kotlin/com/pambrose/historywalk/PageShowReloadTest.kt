package com.pambrose.historywalk

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.browser.document
import org.w3c.dom.PageTransitionEvent
import org.w3c.dom.PageTransitionEventInit
import org.w3c.dom.events.Event
import org.w3c.dom.events.EventTarget

/**
 * Back after a sign-out on a shared computer (issue #11): a page the browser restores from its back/forward cache is
 * reloaded, so the server, not the cache, decides what it shows. Each test listens on an element of its own, never on
 * the window, and passes its own reload, so the Karma page stays where it is.
 */
class PageShowReloadTest : StringSpec() {
  private var reloads = 0

  // A fresh element listening as the page does, with a reload that only counts
  private fun listening(): EventTarget {
    val target = document.createElement("div")
    reloadWhenRestoredFromCache(target) { reloads++ }
    return target
  }

  private fun pageShow(persisted: Boolean) =
    PageTransitionEvent("pageshow", PageTransitionEventInit(persisted = persisted))

  init {
    timeout = CLIENT_TEST_TIMEOUT_MS

    beforeTest { reloads = 0 }

    "an ordinary load's pageshow does not reload the page" {
      val target = listening()

      target.dispatchEvent(pageShow(persisted = false))

      reloads shouldBe 0
    }

    "a page restored from the back/forward cache is reloaded, once" {
      val target = listening()

      target.dispatchEvent(pageShow(persisted = true))

      reloads shouldBe 1
    }

    "other events do not reload the page, not even the pagehide of a page going into the cache" {
      val target = listening()

      target.dispatchEvent(Event("load"))
      target.dispatchEvent(PageTransitionEvent("pagehide", PageTransitionEventInit(persisted = true)))

      reloads shouldBe 0
    }
  }
}
