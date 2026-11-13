package com.pambrose.historywalk

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.browser.document
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.w3c.dom.EventInit
import org.w3c.dom.events.Event
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

/**
 * [PageActivity], which says whether someone is using the page, and [listenForActivity], which feeds it the page's
 * events. Time is a [TestTimeSource] moved by hand, visibility a flag, and the events go to elements of the test's own,
 * never the window, so the Karma page's own events count for nothing here.
 */
class PageActivityTest : StringSpec() {
  private lateinit var time: TestTimeSource
  private var visible = true

  private fun pageActivity() = PageActivity(visible = { visible }, timeSource = time)

  // A page that has gone unused for as long as it takes to be idle
  private fun idlePage() = pageActivity().also { time += PageActivity.IDLE_AFTER }

  // Long enough for a waiter that can return to do so
  private suspend fun settle() = delay(1.milliseconds)

  init {
    timeout = CLIENT_TEST_TIMEOUT_MS
    coroutineTestScope = true

    beforeTest {
      time = TestTimeSource()
      visible = true
    }

    "a page is in use from its load until nobody has done anything in it for IDLE_AFTER" {
      val activity = pageActivity()
      activity.inUse shouldBe true

      time += PageActivity.IDLE_AFTER - 1.seconds
      activity.inUse shouldBe true

      time += 1.seconds
      activity.inUse shouldBe false
    }

    "anything done in the page starts its IDLE_AFTER over" {
      val activity = idlePage()

      activity.active()
      activity.inUse shouldBe true

      time += PageActivity.IDLE_AFTER - 1.seconds
      activity.inUse shouldBe true
      activity.active()
      time += PageActivity.IDLE_AFTER - 1.seconds
      activity.inUse shouldBe true
    }

    "a hidden page is not in use, however recently someone used it, and is again once it is shown" {
      val activity = pageActivity()

      visible = false
      activity.active()
      activity.inUse shouldBe false

      visible = true
      activity.inUse shouldBe true
    }

    "waiting for the page to be in use returns at once while it is" {
      val activity = pageActivity()
      var returned = false

      coroutineScope {
        launch {
          activity.awaitInUse()
          returned = true
        }
        settle()
      }

      returned shouldBe true
    }

    "waiting on an idle page returns only once someone does something in it while it is visible" {
      val activity = idlePage()
      var returned = false

      coroutineScope {
        val waiter =
          launch {
            activity.awaitInUse()
            returned = true
          }
        settle()
        returned shouldBe false

        // Something that reaches a hidden page, such as its going out of view, is not someone using it
        visible = false
        activity.active()
        settle()
        returned shouldBe false

        visible = true
        activity.active()
        settle()
        returned shouldBe true
        waiter.isCompleted shouldBe true
      }
    }

    "each of the page's signs of use makes it in use again" {
      PageActivity.ACTIVITY_EVENTS.forEach { type ->
        val activity = idlePage()
        val target = document.createElement("div")
        listenForActivity(activity, target)

        target.dispatchEvent(Event(type))

        withClue(type) { activity.inUse shouldBe true }
      }
    }

    "other events do not count as someone using the page" {
      val activity = idlePage()
      val target = document.createElement("div")
      listenForActivity(activity, target)

      listOf("load", "message", "resize", "pointerup").forEach { target.dispatchEvent(Event(it)) }

      activity.inUse shouldBe false
    }

    "an event inside the page counts even when it does not bubble or a component stops it" {
      val activity = idlePage()
      val target = document.createElement("div")
      val field = document.createElement("input").also { target.appendChild(it) }
      listenForActivity(activity, target)

      field.dispatchEvent(Event("focus"))
      activity.inUse shouldBe true

      time += PageActivity.IDLE_AFTER
      field.addEventListener("keydown", { it.stopPropagation() })
      field.dispatchEvent(Event("keydown", EventInit(bubbles = true)))
      activity.inUse shouldBe true
    }
  }
}
