package com.pambrose.historywalk

import com.pambrose.historywalk.VersionCheckAction.COUNT_DOWN
import com.pambrose.historywalk.VersionCheckAction.HOLD_DIALOG
import com.pambrose.historywalk.VersionCheckAction.PUT_OFF_RELOAD
import com.pambrose.historywalk.VersionCheckAction.SHOW_DIALOG
import com.pambrose.historywalk.VersionCheckAction.UP_TO_DATE
import com.pambrose.historywalk.VersionCheckAction.WAIT
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The page's side of the version check (issues #16 and #17), with no DOM: the warning time starts only when the update
 * dialog is really shown, a reload is put off while a reason is being written, and a reload that brought back the same
 * bundle is not repeated.
 */
class VersionCheckStateTest : StringSpec() {
  private val memory = FakeReloadMemory()
  private lateinit var state: VersionCheckState

  // A server answer, with the flags the page passes alongside it; a test names only the flag it sets
  private fun VersionCheckState.decideOn(
    response: VersionCheckResponse,
    modalOpen: Boolean = false,
    writingReason: Boolean = false,
  ) = decide(response, modalOpen, writingReason)

  // The stamp the next check sends the server, which is what starts and keeps its warning time
  private val VersionCheckState.echoedStamp get() = request().reloadWarningInstant

  init {
    timeout = CLIENT_TEST_TIMEOUT_MS

    beforeTest {
      memory.reloadedFor = null
      state = VersionCheckState(TEST_BUNDLE_VERSION, TEST_BUNDLE_API_VERSION, memory)
    }

    "the first check sends the bundle's versions and no stamp" {
      state.request() shouldBe VersionCheckInfo(TEST_BUNDLE_VERSION, TEST_BUNDLE_API_VERSION, null)
    }

    "a DIALOG answer while another modal is open is held back, and starts no warning time" {
      state.decideOn(dialogAnswer(), modalOpen = true) shouldBe HOLD_DIALOG

      state.echoedStamp.shouldBeNull()
      state.dialogShown shouldBe false
    }

    "a DIALOG answer while a reason is being written is held back, and starts no warning time" {
      state.decideOn(dialogAnswer(), writingReason = true) shouldBe HOLD_DIALOG

      state.echoedStamp.shouldBeNull()
      state.dialogShown shouldBe false
    }

    "the dialog actually shown starts the warning time with its own answer's stamp, which every check sends back" {
      val heldBack = Instant.parse("2026-11-01T09:00:00Z")
      val shown = Instant.parse("2026-11-01T09:00:15Z")

      // The server stamps each answer with its own time until the page sends one back
      state.decideOn(dialogAnswer(heldBack), writingReason = true) shouldBe HOLD_DIALOG
      state.decideOn(dialogAnswer(shown)) shouldBe SHOW_DIALOG

      state.dialogOpen shouldBe true
      state.reloadWarningInstant shouldBe shown
      state.request() shouldBe VersionCheckInfo(TEST_BUNDLE_VERSION, TEST_BUNDLE_API_VERSION, shown)
    }

    "a DIALOG answer while the update dialog is open waits, whatever else is open" {
      state.decideOn(dialogAnswer()) shouldBe SHOW_DIALOG

      state.decideOn(dialogAnswer()) shouldBe WAIT
      state.decideOn(dialogAnswer(), modalOpen = true, writingReason = true) shouldBe WAIT
    }

    "a dialog shown again after it was closed keeps the first stamp" {
      state.decideOn(dialogAnswer(firstWarning)) shouldBe SHOW_DIALOG
      state.dialogClosed()
      state.dialogOpen shouldBe false

      state.decideOn(dialogAnswer(firstWarning + 30.seconds)) shouldBe SHOW_DIALOG

      state.echoedStamp shouldBe firstWarning
    }

    "a RELOAD answer while a reason is being written is put off, and the stamp kept so the next check asks again" {
      state.decideOn(dialogAnswer(firstWarning)) shouldBe SHOW_DIALOG

      state.decideOn(reloadAnswer(), writingReason = true) shouldBe PUT_OFF_RELOAD

      state.echoedStamp shouldBe firstWarning
    }

    "a RELOAD answer otherwise counts down, even with the update dialog itself still open" {
      state.decideOn(dialogAnswer()) shouldBe SHOW_DIALOG

      // The app's modalOpen counts the update dialog too, so the countdown must not wait for a modal to close
      state.decideOn(reloadAnswer(), modalOpen = true) shouldBe COUNT_DOWN
      state.decideOn(reloadAnswer()) shouldBe COUNT_DOWN
    }

    "once the countdown runs out, a reason being written stops the reload, and nothing is recorded" {
      state.reloadNow(writingReason = true) shouldBe false

      memory.reloadedFor.shouldBeNull()
      state.reloadDidNotHelp shouldBe false
    }

    "once the countdown runs out, the page records its bundle's apiVersion before it reloads" {
      state.reloadNow(writingReason = false) shouldBe true

      memory.reloadedFor shouldBe TEST_BUNDLE_API_VERSION
      state.reloadDidNotHelp shouldBe true
    }

    "after a reload that brought back the same bundle, the dialog is shown once and no stamp is ever sent" {
      memory.reloadedFor = TEST_BUNDLE_API_VERSION
      state = VersionCheckState(TEST_BUNDLE_VERSION, TEST_BUNDLE_API_VERSION, memory)
      state.reloadDidNotHelp shouldBe true

      state.decideOn(dialogAnswer(firstWarning)) shouldBe SHOW_DIALOG
      state.echoedStamp.shouldBeNull()

      state.dialogClosed()
      repeat(5) { state.decideOn(dialogAnswer(firstWarning + it.seconds)) shouldBe WAIT }
      state.echoedStamp.shouldBeNull()
    }

    "after a reload that brought back the same bundle, a dialog held back is still shown once it can be" {
      memory.reloadedFor = TEST_BUNDLE_API_VERSION
      state = VersionCheckState(TEST_BUNDLE_VERSION, TEST_BUNDLE_API_VERSION, memory)

      state.decideOn(dialogAnswer(), writingReason = true) shouldBe HOLD_DIALOG
      state.decideOn(dialogAnswer()) shouldBe SHOW_DIALOG
      state.echoedStamp.shouldBeNull()
    }

    "a RELOAD answer to a page that has already reloaded for its bundle waits, and drops the stamp" {
      // The page showed the dialog and took the stamp, recorded its reload, and is somehow still here
      state.decideOn(dialogAnswer(firstWarning)) shouldBe SHOW_DIALOG
      state.reloadNow(writingReason = false) shouldBe true

      state.decideOn(reloadAnswer()) shouldBe WAIT

      state.echoedStamp.shouldBeNull()
    }

    "a recorded reload for another apiVersion does not hold back this bundle's warning or reload" {
      memory.reloadedFor = "6"
      state = VersionCheckState(TEST_BUNDLE_VERSION, TEST_BUNDLE_API_VERSION, memory)
      state.reloadDidNotHelp shouldBe false

      state.decideOn(dialogAnswer(firstWarning)) shouldBe SHOW_DIALOG
      state.echoedStamp shouldBe firstWarning
      state.decideOn(reloadAnswer()) shouldBe COUNT_DOWN
      state.reloadNow(writingReason = false) shouldBe true
      memory.reloadedFor shouldBe TEST_BUNDLE_API_VERSION
    }

    "an up-to-date answer forgets the stamp" {
      state.decideOn(dialogAnswer(firstWarning)) shouldBe SHOW_DIALOG

      state.decideOn(upToDateAnswer()) shouldBe UP_TO_DATE

      state.echoedStamp.shouldBeNull()
    }

    "an up-to-date answer forgets the recorded reload, so a later mismatch warns and reloads again" {
      memory.reloadedFor = TEST_BUNDLE_API_VERSION
      state = VersionCheckState(TEST_BUNDLE_VERSION, TEST_BUNDLE_API_VERSION, memory)

      state.decideOn(upToDateAnswer()) shouldBe UP_TO_DATE

      memory.reloadedFor.shouldBeNull()
      state.reloadDidNotHelp shouldBe false
      state.decideOn(dialogAnswer(firstWarning)) shouldBe SHOW_DIALOG
      state.echoedStamp shouldBe firstWarning
    }

    "the wait between checks follows the server's answer, and keeps its value when an answer carries none" {
      state.checkInterval shouldBe VersionCheckState.DEFAULT_CHECK_INTERVAL

      state.decideOn(dialogAnswer(checkInterval = 15.seconds), modalOpen = true) shouldBe HOLD_DIALOG
      state.checkInterval shouldBe 15.seconds

      state.decideOn(reloadAnswer(), writingReason = true) shouldBe PUT_OFF_RELOAD
      state.checkInterval shouldBe 15.seconds

      state.decideOn(upToDateAnswer(checkInterval = 90.seconds)) shouldBe UP_TO_DATE
      state.checkInterval shouldBe 90.seconds

      state.decideOn(upToDateAnswer(checkInterval = null)) shouldBe UP_TO_DATE
      state.checkInterval shouldBe 90.seconds
    }
  }
}

/** [ReloadMemory] in a field, standing in for the tab's sessionStorage, which outlives the page's own reloads. */
class FakeReloadMemory(
  override var reloadedFor: String? = null,
) : ReloadMemory

// The version-check specs' bundle; the server has moved on to another apiVersion, so it answers DIALOG or RELOAD
const val TEST_BUNDLE_VERSION = "1.4.0"
const val TEST_BUNDLE_API_VERSION = "7"

/** When the server first warned the page, as it stamps a DIALOG answer. */
val firstWarning: Instant = Instant.parse("2026-11-01T09:00:00Z")

/** A DIALOG answer as ServerVersionCheck gives one while the warning time runs, which started at [stamp]. */
fun dialogAnswer(
  stamp: Instant = firstWarning,
  checkInterval: Duration? = 15.seconds,
) = VersionCheckResponse(
  AlertLevel.DIALOG,
  title = "Application Update",
  message = "A new version of History Walk is available. " +
    "Please <a href='/'>click here</a> to reload this page and continue.",
  dialogConfig = DialogConfig(rich = true, size = "LARGE"),
  reloadWarningInstant = stamp,
  versionCheckInterval = checkInterval,
)

/** A RELOAD answer as ServerVersionCheck gives one once the warning time is up: a [countdown], then the reload. */
fun reloadAnswer(countdown: Duration = 15.seconds) =
  VersionCheckResponse(
    AlertLevel.RELOAD,
    title = "Application Update",
    message = "A new version of History Walk is available. Reloading this page in {{COUNT}} seconds...",
    toastConfig = ToastConfig(color = "WHITE", bgColor = "DANGER", autohide = true, duration = countdown),
  )

/** The answer to a page whose bundle matches the server's. */
fun upToDateAnswer(checkInterval: Duration? = 60.seconds) =
  VersionCheckResponse(AlertLevel.NONE, versionCheckInterval = checkInterval)
