package com.pambrose.historywalk

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import org.w3c.dom.AddEventListenerOptions
import org.w3c.dom.events.EventTarget
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

/**
 * Whether someone is using the page: it is [visible], and someone did something in it ([active]) less than
 * [IDLE_AFTER] ago. The version check waits on it ([awaitInUse]), so a page left open with nobody at it, such as a signed-out one
 * behind the sign-in dialog, stops calling the server, and the first thing anyone does in it brings a check. The page
 * counts as in use from its load. It touches no DOM: [listenForActivity] feeds it the page's events.
 *
 * @param visible whether the page can be seen: not a background tab or a minimized window ([pageVisible])
 */
internal class PageActivity(
  private val visible: () -> Boolean = ::pageVisible,
  private val timeSource: TimeSource = TimeSource.Monotonic,
) {
  private var lastActive = timeSource.markNow()

  // Each activity, for whoever is waiting in awaitInUse; with nobody waiting, an emit is dropped at once
  private val activity = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = DROP_OLDEST)

  /** Whether the page is [visible] and someone did something in it less than [IDLE_AFTER] ago. */
  val inUse: Boolean
    get() = visible() && lastActive.elapsedNow() < IDLE_AFTER

  /** Someone did something in the page, or it came back into view. */
  fun active() {
    lastActive = timeSource.markNow()
    activity.tryEmit(Unit)
  }

  /**
   * Returns at once while the page is [inUse], else once it is: the next time someone does something in it while it
   * is visible, the tab coming back into view included.
   */
  suspend fun awaitInUse() {
    while (!inUse) activity.first()
  }

  companion object {
    /** How long a visible page goes with nobody doing anything in it before it no longer counts as in use. */
    val IDLE_AFTER = 5.minutes

    /**
     * What counts as someone using the page: a click or a tap, a key, the wheel or a scroll, the mouse or a finger
     * moving, the page or one of its fields getting the focus, and the tab coming back into view (or going out of it,
     * which [inUse] sees for itself).
     */
    val ACTIVITY_EVENTS =
      listOf("pointerdown", "pointermove", "touchstart", "keydown", "wheel", "scroll", "focus", "visibilitychange")
  }
}

/** Whether the page can be seen. A browser that does not say (no Page Visibility API) counts as showing it. */
internal fun pageVisible(): Boolean = document.asDynamic().visibilityState != "hidden"

/**
 * Tells [activity] about each of [PageActivity.ACTIVITY_EVENTS] that reaches [target] (the window unless a test passes
 * another). It listens in the capture phase, so it sees the events that do not bubble (a scroll, a field's focus) and
 * those a component stops, and passively, so scrolling never waits on it.
 */
internal fun listenForActivity(
  activity: PageActivity,
  target: EventTarget = window,
) {
  val options = AddEventListenerOptions(passive = true, capture = true)
  PageActivity.ACTIVITY_EVENTS.forEach { type -> target.addEventListener(type, { activity.active() }, options) }
}
