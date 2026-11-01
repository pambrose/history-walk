package com.pambrose.historywalk

import com.pambrose.historywalk.ClientUtils.CLICK_FAILED
import dev.kilua.rpc.ServiceException
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kvision.html.Button
import io.kvision.panel.SimplePanel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import org.w3c.dom.HTMLButtonElement
import kotlin.time.Duration.Companion.milliseconds

/**
 * The `busy` gate on the choice and go-back buttons (issue #6): one click's request at a time, every such button
 * disabled meanwhile, and enabled again however the request ends.
 */
class OnClickWhenIdleTest : StringSpec() {
  private fun idleButton(
    text: String,
    action: suspend () -> Unit,
  ) = Button(text).apply { onClickWhenIdle(action) }

  private val Button.disabledOnPage get() = (element as HTMLButtonElement).disabled

  init {
    timeout = CLIENT_TEST_TIMEOUT_MS

    beforeTest { FakeServer.reset() }
    afterTest { busy.value = false }

    "while one click's request is in flight every such button is disabled, and further clicks run nothing" {
      val release = CompletableDeferred<Unit>()
      var runs = 0
      val north = idleButton("Go north") {
        runs++
        release.await()
      }
      val south = idleButton("Go south") { runs++ }

      val buttons = SimplePanel()
      buttons.add(north)
      buttons.add(south)

      TestPage.showing(buttons) {
        north.click()
        awaitUntil { north.disabledOnPage && south.disabledOnPage }

        north.click()
        south.click()
        delay(50.milliseconds)
        runs shouldBe 1

        release.complete(Unit)
        awaitUntil { !busy.value }
        north.disabledOnPage shouldBe false
        south.disabledOnPage shouldBe false
      }
    }

    "a double-click runs the action once" {
      val release = CompletableDeferred<Unit>()
      var runs = 0
      val choice = idleButton("Go north") {
        runs++
        release.await()
      }

      TestPage.showing(choice) {
        choice.click()
        choice.click()
        release.complete(Unit)
        awaitUntil { !busy.value }

        runs shouldBe 1
      }
    }

    "after a request succeeds the buttons work again" {
      var runs = 0
      val choice = idleButton("Go north") { runs++ }

      TestPage.showing(choice) {
        toastsShownBy {
          choice.click()
          awaitUntil { runs == 1 && !busy.value }
          choice.click()
          awaitUntil { runs == 2 && !busy.value }
        }.shouldBeEmpty()
        choice.disabledOnPage shouldBe false
      }
    }

    "a request that fails shows a toast and enables the buttons again" {
      val choice = idleButton("Go north") { error("Service Unavailable") }

      TestPage.showing(choice) {
        toastsShownBy {
          choice.click()
          awaitUntil { !busy.value }
        } shouldBe listOf(CLICK_FAILED)
        choice.disabledOnPage shouldBe false
      }
    }

    "a refused move shows the server's reason, reloads the student's real slide, and enables the buttons again" {
      val refusal = "That choice is not on the slide you are on"
      FakeServer.on(IContentService::getCurrentSlide) { returns(testSlide("Where you really are")) }
      val choice = idleButton("Go north") { throw ServiceException(refusal) }

      TestPage.showing(choice) {
        toastsShownBy {
          choice.click()
          awaitUntil { !busy.value }
        } shouldBe listOf(refusal)

        FakeServer.callsTo(IContentService::getCurrentSlide) shouldHaveSize 1
        awaitUntil { "Where you really are" in MainPanel.panel.texts() }
        choice.disabledOnPage shouldBe false
      }
    }

    "a disposed button stops following the busy flag" {
      val choice = idleButton("Go north") {}
      choice.dispose()

      busy.value = true

      choice.disabled shouldBe false
    }
  }
}
