package com.pambrose.historywalk

import dev.kilua.rpc.SecurityException
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * [VersionCheckLoop] with fakes for the server, the page's flags, and the UI, so nothing here opens a dialog or leaves
 * the Karma page. The spec runs in Kotest's coroutine test scope: the loop's waits (the check interval, the countdown's
 * seconds) are virtual, and a `delay` in a test moves the clock on, running every check due by then. The scheduler
 * itself is out of reach, as kotlinx-coroutines-test is only on the runtime classpath (through Kotest), so `delay` is
 * the clock.
 */
class VersionCheckLoopTest : StringSpec() {
  /** Records what the loop shows; [reload] only counts, so the page under test stays where it is. */
  private class FakeUi : VersionCheckUi {
    val dialogs = mutableListOf<VersionCheckResponse>()
    val countdown = mutableListOf<Int>()
    var reloads = 0
    private var onClosed: (() -> Unit)? = null

    override fun showDialog(
      response: VersionCheckResponse,
      onClosed: () -> Unit,
    ) {
      dialogs += response
      this.onClosed = onClosed
    }

    override fun showCountdown(
      response: VersionCheckResponse,
      secondsLeft: Int,
    ) {
      countdown += secondsLeft
    }

    override fun reload() {
      reloads++
    }

    /** The student closes the update dialog. */
    fun closeDialog() {
      onClosed?.invoke()
      onClosed = null
    }
  }

  private lateinit var memory: FakeReloadMemory
  private lateinit var ui: FakeUi
  private var modalOpen = false
  private var writingReason = false

  // What the page sent with each check, oldest first
  private val requests = mutableListOf<VersionCheckInfo>()

  // The server's answer to each check; a test replaces it
  private var answer: (VersionCheckInfo) -> VersionCheckResponse = { upToDateAnswer() }

  // As ServerVersionCheck answers while the warning time runs: a DIALOG carrying the stamp the page sent, or, if it
  // sent none, the time of this check. stampOf(n) is that time for check n (from 0, the checks 15 seconds apart), so a
  // test can tell which answer a stamp came from.
  private fun stampOf(check: Int): Instant = firstWarning + (check * 15).seconds

  private val warnsWithDialog: (VersionCheckInfo) -> VersionCheckResponse = { request ->
    dialogAnswer(request.reloadWarningInstant ?: stampOf(requests.lastIndex))
  }

  private fun loop() =
    VersionCheckLoop(
      state = VersionCheckState(TEST_BUNDLE_VERSION, TEST_BUNDLE_API_VERSION, memory),
      versionCheck = { request ->
        requests += request
        answer(request)
      },
      modalOpen = { modalOpen },
      writingReason = { writingReason },
      ui = ui,
    )

  /**
   * Runs the loop beside [test], which moves the virtual clock with `delay`, and stops it at the end. The loop never
   * ends by itself unless it reloads, and the test scope would otherwise run its checks forever.
   */
  private suspend fun runningLoop(test: suspend (Job) -> Unit) =
    coroutineScope {
      val job = launch { loop().run() }
      try {
        test(job)
      } finally {
        job.cancel()
      }
    }

  // Long enough for whatever is due now, such as the first check, to run; short of any wait in the loop
  private suspend fun settle() = delay(1.milliseconds)

  init {
    timeout = CLIENT_TEST_TIMEOUT_MS
    coroutineTestScope = true

    beforeTest {
      memory = FakeReloadMemory()
      ui = FakeUi()
      modalOpen = false
      writingReason = false
      requests.clear()
      answer = { upToDateAnswer() }
    }

    "a page that is up to date shows nothing, and checks again at the interval the server gives" {
      answer = { upToDateAnswer(checkInterval = 60.seconds) }

      runningLoop {
        settle()
        requests shouldHaveSize 1
        delay(59.seconds)
        requests shouldHaveSize 1
        delay(1.seconds)
        requests shouldHaveSize 2
      }

      ui.dialogs.shouldBeEmpty()
      ui.countdown.shouldBeEmpty()
    }

    "a mismatch shows the update dialog once, and the stamp goes back only from the answer whose dialog was shown" {
      answer = warnsWithDialog

      runningLoop {
        settle()
        ui.dialogs shouldHaveSize 1
        requests.single().reloadWarningInstant.shouldBeNull()

        // The dialog's answers ask for a check every 15 seconds
        delay(60.seconds)
        ui.dialogs shouldHaveSize 1
        requests shouldHaveSize 5
        requests.drop(1).map { it.reloadWarningInstant }.toSet() shouldBe setOf(stampOf(0))
      }
    }

    "a dialog held back by another modal is shown once the modal closes, and its warning time starts then" {
      answer = warnsWithDialog
      modalOpen = true

      runningLoop {
        settle()
        delay(15.seconds)
        ui.dialogs.shouldBeEmpty()
        requests shouldHaveSize 2

        modalOpen = false
        delay(15.seconds)
        ui.dialogs shouldHaveSize 1

        delay(15.seconds)
        requests.map { it.reloadWarningInstant } shouldBe listOf(null, null, null, stampOf(2))
      }
    }

    "a dialog closed by the student is shown again by the next check that still finds a mismatch" {
      answer = warnsWithDialog

      runningLoop {
        settle()
        delay(15.seconds)
        ui.dialogs shouldHaveSize 1

        ui.closeDialog()
        delay(15.seconds)
        ui.dialogs shouldHaveSize 2
        requests.last().reloadWarningInstant shouldBe stampOf(0)
      }
    }

    "when the warning time is up the page counts down a second at a time, then records its bundle and reloads" {
      answer = { reloadAnswer(countdown = 5.seconds) }

      runningLoop { job ->
        settle()
        ui.countdown shouldBe listOf(5)
        ui.reloads shouldBe 0

        delay(5.seconds)
        ui.countdown shouldBe listOf(5, 4, 3, 2, 1)
        ui.reloads shouldBe 1
        memory.reloadedFor shouldBe TEST_BUNDLE_API_VERSION

        // Reloading ends the loop: no further check
        job.isCompleted shouldBe true
        delay(5.minutes)
        requests shouldHaveSize 1
      }
    }

    "a reason started during the countdown stops it, with no reload, and a later check asks again" {
      answer = { reloadAnswer(countdown = 5.seconds) }

      runningLoop { job ->
        settle()
        delay(2.seconds)
        ui.countdown shouldBe listOf(5, 4, 3)

        writingReason = true
        delay(1.seconds)
        ui.countdown shouldBe listOf(5, 4, 3)
        ui.reloads shouldBe 0
        memory.reloadedFor.shouldBeNull()
        job.isActive shouldBe true

        // A RELOAD answer carries no interval, so the next check comes after the default wait, and is put off too
        delay(VersionCheckState.DEFAULT_CHECK_INTERVAL)
        requests shouldHaveSize 2
        ui.countdown shouldHaveSize 3

        writingReason = false
        delay(VersionCheckState.DEFAULT_CHECK_INTERVAL)
        requests shouldHaveSize 3
        delay(5.seconds)
        ui.countdown shouldBe listOf(5, 4, 3, 5, 4, 3, 2, 1)
        ui.reloads shouldBe 1
      }
    }

    "a reason started in the countdown's last second still stops the reload" {
      answer = { reloadAnswer(countdown = 2.seconds) }

      runningLoop { job ->
        settle()
        delay(1.seconds)
        ui.countdown shouldBe listOf(2, 1)

        writingReason = true
        delay(1.seconds)
        ui.reloads shouldBe 0
        memory.reloadedFor.shouldBeNull()
        job.isActive shouldBe true
      }
    }

    "a check that fails, signed out or otherwise, is passed over, and the loop checks again" {
      var checks = 0
      answer = {
        when (++checks) {
          1 -> throw SecurityException("Unauthorized")
          2 -> throw IllegalStateException("Service Unavailable")
          else -> dialogAnswer()
        }
      }

      runningLoop { job ->
        settle()
        job.isActive shouldBe true
        ui.dialogs.shouldBeEmpty()

        // No answer, so no interval from the server: the default wait
        delay(VersionCheckState.DEFAULT_CHECK_INTERVAL)
        job.isActive shouldBe true
        requests shouldHaveSize 2

        delay(VersionCheckState.DEFAULT_CHECK_INTERVAL)
        requests shouldHaveSize 3
        ui.dialogs shouldHaveSize 1
      }
    }
  }
}
