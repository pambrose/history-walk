package com.pambrose.historywalk

import com.pambrose.historywalk.ReasonRule.MAX_LENGTH
import com.pambrose.historywalk.ReasonRule.MIN_WORDS
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kvision.modal.Dialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async

/**
 * The reason dialog's gate (issue #8): OK stays disabled until the reason has [MIN_WORDS] words, counted from the box's
 * input event, so dictated, pasted, and dropped text count as typed text does; the dialog says so, and the box takes
 * no more than the server accepts.
 */
class ReasonDialogTest : StringSpec() {
  private val choice = testChoice("Go north")
  private val opened = mutableListOf<Dialog<String>>()

  private suspend fun shownReasonDialog(draft: String? = null): Dialog<String> =
    reasonDialog(choice, draft).also {
      opened += it
      showModal(it)
    }

  private val Dialog<String>.ok get() = element.button("OK")

  init {
    timeout = CLIENT_TEST_TIMEOUT_MS

    afterTest {
      closeModals()
      opened.forEach { it.dispose() }
      opened.clear()
    }

    "OK stays disabled until the reason has $MIN_WORDS words, with no key events at all" {
      val dialog = shownReasonDialog()
      dialog.ok.disabled shouldBe true

      dialog.element.textArea.enter("it looked like the")
      dialog.ok.disabled shouldBe true
      dialog.element.textContent.orEmpty() shouldContain "4 words"

      dialog.element.textArea.enter("it looked like the safest")
      dialog.ok.disabled shouldBe false
      dialog.element.textContent.orEmpty() shouldContain "5 words"

      dialog.element.textArea.enter("it looked")
      dialog.ok.disabled shouldBe true
    }

    "spaces, tabs, and newlines are not words" {
      val dialog = shownReasonDialog()

      dialog.element.textArea.enter("   one\t\ttwo\n\nthree  four    ")

      dialog.ok.disabled shouldBe true
      dialog.element.textContent.orEmpty() shouldContain "4 words"
    }

    "one word is counted as 1 word" {
      val dialog = shownReasonDialog()

      dialog.element.textArea.enter("because")

      dialog.element.textContent.orEmpty() shouldContain "1 word"
    }

    "the dialog says how many words a reason needs, and names the choice" {
      val dialog = shownReasonDialog()
      val text = dialog.element.textContent.orEmpty()

      text shouldContain "Use at least $MIN_WORDS words."
      text shouldContain "Reason for your \"Go north\" decision:"
    }

    "the reason box takes at most the length the server accepts" {
      val dialog = shownReasonDialog()

      dialog.element.textArea.maxLength shouldBe MAX_LENGTH
    }

    "a reason kept from a failed save is back in the box and already counts" {
      val draft = "the river crossing looked safest"
      val dialog = shownReasonDialog(draft)

      dialog.element.textArea.value shouldBe draft
      dialog.ok.disabled shouldBe false
    }

    "OK returns the reason, and Cancel returns nothing" {
      val reason = "the north road had the most people on it"
      val answered = reasonDialog(choice, null).also { opened += it }
      val result = CoroutineScope(Dispatchers.Default).async { answered.getResult() }
      awaitUntil { answered.visible && answered.getElement() != null }
      answered.element.textArea.enter(reason)
      answered.ok.click()
      result.await() shouldBe reason

      val cancelled = reasonDialog(choice, null).also { opened += it }
      val nothing = CoroutineScope(Dispatchers.Default).async { cancelled.getResult() }
      awaitUntil { cancelled.visible && cancelled.getElement() != null }
      cancelled.element.button("Cancel").click()
      nothing.await() shouldBe ""
    }
  }
}
