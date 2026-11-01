package com.pambrose.historywalk

import io.kvision.core.Component
import io.kvision.core.Container
import io.kvision.core.Widget
import io.kvision.html.Tag
import io.kvision.html.TextNode
import io.kvision.modal.Modal
import io.kvision.panel.Root
import kotlinx.browser.document
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.w3c.dom.Element
import org.w3c.dom.EventInit
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.dom.asList
import org.w3c.dom.events.Event
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The page the client specs draw into: one KVision [Root] for the whole run, on a host element of its own.
 *
 * KVision draws every modal and toast (the dialogs, ClientUtils' toasts) through the page's first Root, and forgets
 * them all when that Root is disposed, so the specs share this one rather than each making and disposing its own. It
 * renders synchronously, bindings included, so a change shows in the DOM as soon as it is made.
 *
 * The page has no server behind it, so [FakeServer] answers its calls from the start: a call that reached the test
 * runner instead would fail, and KVision would take that first failure for a signed-out user (see [FakeServer]).
 */
object TestPage {
  val root: Root by lazy {
    FakeServer.install()
    val host = document.createElement("div") as HTMLElement
    document.body!!.appendChild(host)
    Root(host) { synchronousMode = true }
  }

  /** Puts [widget] on the page for the length of [test], then takes it off and disposes it. */
  suspend fun <W : Widget> showing(
    widget: W,
    test: suspend (W) -> Unit,
  ) {
    root.add(widget)
    try {
      test(widget)
    } finally {
      root.remove(widget)
      widget.dispose()
    }
  }
}

/**
 * The longest a client test may run, in milliseconds; each JS spec sets it as its `timeout`. Kotest's default is ten
 * minutes, and Kotest reports nothing to Karma until every spec has run, so a test that hangs would otherwise end the
 * run as Karma giving up on a silent browser (karma.config.d/kotest.js), without the test's name. It is set in each
 * spec rather than in a Kotest ProjectConfig because the Kotest KSP processor drops a ProjectConfig from the JS entry
 * point it generates whenever KSP reprocesses incrementally after an edit to a file that holds no spec.
 */
const val CLIENT_TEST_TIMEOUT_MS = 15_000L

/** Waits until [check] holds, polling every 10ms, and fails after [timeout]. */
suspend fun awaitUntil(
  timeout: Duration = 2.seconds,
  check: () -> Boolean,
) {
  withTimeout(timeout) {
    while (!check()) delay(10.milliseconds)
  }
}

/** Every component under this one, at any depth. */
fun Component.descendants(): List<Component> =
  if (this is Container) getChildren().flatMap { listOf(it) + it.descendants() } else emptyList()

/**
 * The texts under this component (a tag's content, and the text a `+"..."` adds), read from the component tree, so it
 * works for a panel that is not on the page.
 */
fun Component.texts(): List<String> =
  descendants().mapNotNull {
    when (it) {
      is TextNode -> it.content
      is Tag -> it.content
      else -> null
    }
  }

/** This widget's element; the widget must be on the page. */
val Widget.element: HTMLElement
  get() = getElement() ?: error("$this is not on the page")

/** Clicks the widget as a user does: a DOM click, which a disabled button ignores. */
fun Widget.click() = element.click()

/** The one button under this element whose text is [text]. */
fun Element.button(text: String): HTMLButtonElement =
  querySelectorAll("button")
    .asList()
    .filterIsInstance<HTMLButtonElement>()
    .single { it.textContent.orEmpty().trim() == text }

/** The one textarea under this element. */
val Element.textArea: HTMLTextAreaElement
  get() = querySelectorAll("textarea").asList().filterIsInstance<HTMLTextAreaElement>().single()

/**
 * Puts [text] in the box as dictation, a paste, or drag-and-drop does: the value changes and the box hears an input
 * event, with no key events at all.
 */
fun HTMLTextAreaElement.enter(text: String) {
  value = text
  dispatchEvent(Event("input", EventInit(bubbles = true)))
}

private fun toastElements(): List<Element> = document.querySelectorAll(".toast").asList().filterIsInstance<Element>()

/** The text of each toast that [block] shows, in order (a toast stays on the page a few seconds, so specs compare). */
suspend fun toastsShownBy(block: suspend () -> Unit): List<String> {
  TestPage.root
  val before = toastElements().toSet()
  block()
  return toastElements().filter { it !in before }.map { it.textContent.orEmpty().trim() }
}

/**
 * Shows [modal] and waits until Bootstrap has finished opening it. Bootstrap ignores a hide that comes while a modal
 * is still opening, which would leave it on the page for the specs that follow.
 */
suspend fun showModal(modal: Modal) {
  TestPage.root
  val shown = CompletableDeferred<Unit>()
  val listener: (Event) -> Unit = { if (it.target == modal.getElement()) shown.complete(Unit) }
  document.addEventListener("shown.bs.modal", listener)
  try {
    modal.show()
    withTimeout(2.seconds) { shown.await() }
  } finally {
    document.removeEventListener("shown.bs.modal", listener)
  }
}

/** The modals showing on the page. */
fun visibleModals(): List<Modal> = Modal.getAllModals().filter { it.visible }

/**
 * Hides every modal still showing, and clears what Bootstrap leaves behind, as KVision's own DomSpec does. A spec that
 * opens a dialog ends with this: KVision keeps one list of modals for the whole page.
 */
fun closeModals() {
  visibleModals().forEach { it.hide() }
  document.querySelectorAll(".modal-backdrop").asList().forEach { (it as Element).remove() }
  document.body?.classList?.remove("modal-open")
  document.body?.removeAttribute("style")
}
