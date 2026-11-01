package com.pambrose.historywalk

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kvision.modal.Modal
import kotlinx.browser.document
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.KeyboardEventInit
import kotlin.time.Duration.Companion.seconds

/**
 * The version check's dialog and countdown as KVision shows them (issue #21): once the student closes the update
 * dialog, it is disposed, so neither KVision's list of modals nor the page keeps it, and Bootstrap's backdrop goes
 * with it. [KVisionVersionCheckUi.reload] is never called here, as it would navigate the Karma page away.
 */
class KVisionVersionCheckUiTest : StringSpec() {
  private var closes = 0

  /** Shows the update dialog for [response] and waits until Bootstrap has finished opening it (see [showModal]). */
  private suspend fun shownUpdateDialog(response: VersionCheckResponse = dialogAnswer()): Modal {
    TestPage.root
    val before = Modal.getAllModals().toSet()
    val shown = CompletableDeferred<Unit>()
    val listener: (Event) -> Unit = { shown.complete(Unit) }
    document.addEventListener("shown.bs.modal", listener)
    try {
      KVisionVersionCheckUi.showDialog(response) { closes++ }
      withTimeout(2.seconds) { shown.await() }
    } finally {
      document.removeEventListener("shown.bs.modal", listener)
    }
    return Modal.getAllModals().single { it !in before }
  }

  /** Waits until [dialog], whose element was [element], has left KVision's modals, the page, and the backdrop. */
  private suspend fun awaitGone(
    dialog: Modal,
    element: HTMLElement,
  ) {
    awaitUntil {
      dialog !in Modal.getAllModals() &&
        !element.isConnected &&
        document.querySelector(".modal-backdrop") == null &&
        document.body?.classList?.contains("modal-open") == false
    }
  }

  init {
    timeout = CLIENT_TEST_TIMEOUT_MS

    beforeTest { closes = 0 }

    // Only for a failed test, which could leave its dialog open for the specs that follow
    afterTest { closeModals() }

    "the update dialog shows the server's title, message, and reload link" {
      val dialog = shownUpdateDialog()
      val element = dialog.element

      dialog.caption shouldBe "Application Update"
      element.textContent.orEmpty() shouldContain "A new version of History Walk is available"
      element.querySelector("a")?.getAttribute("href") shouldBe "/"

      // Closed and gone before the next test, so its Bootstrap hide cannot finish during the next one's
      element.button("OK").click()
      awaitGone(dialog, element)
    }

    "closing the update dialog with OK disposes it and leaves no backdrop, and onClosed runs once" {
      val dialog = shownUpdateDialog()
      val element = dialog.element

      element.button("OK").click()
      awaitGone(dialog, element)

      closes shouldBe 1
    }

    "closing the update dialog with Escape disposes it too" {
      val dialog = shownUpdateDialog()
      val element = dialog.element

      element.dispatchEvent(KeyboardEvent("keydown", KeyboardEventInit(key = "Escape", bubbles = true)))
      awaitGone(dialog, element)

      closes shouldBe 1
    }

    "the countdown toast counts the seconds left in place of {{COUNT}}" {
      val toasts = toastsShownBy { KVisionVersionCheckUi.showCountdown(reloadAnswer(), 7) }

      toasts.single() shouldContain "Reloading this page in 7 seconds..."
      toasts.single() shouldNotContain "{{COUNT}}"
    }
  }
}
