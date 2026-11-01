package com.pambrose.historywalk

import com.pambrose.BuildConfig
import com.pambrose.historywalk.ClientUtils.toastContainer
import com.pambrose.historywalk.VersionCheckAction.COUNT_DOWN
import com.pambrose.historywalk.VersionCheckAction.SHOW_DIALOG
import dev.kilua.rpc.SecurityException
import dev.kilua.rpc.getService
import io.kvision.core.BsBgColor
import io.kvision.core.BsColor
import io.kvision.modal.Alert
import io.kvision.modal.Modal
import io.kvision.modal.ModalSize
import kotlinx.browser.window
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

/**
 * Asks the server, every so often, whether this page's bundle is still current (ServerVersionCheck), so a page left
 * open across a release that changes `apiVersion` is asked to reload, then reloads itself. The check goes around
 * `withAuth`: a signed-out page is left to the sign-in dialog, and its failed checks are retried quietly. The decisions
 * are [VersionCheckState]'s, and [VersionCheckLoop] runs them.
 */
object ClientVersionCheck {
  private val contentService = getService<IContentService>()

  suspend fun checkClientVersion() =
    VersionCheckLoop(
      state = VersionCheckState(BuildConfig.VERSION, BuildConfig.API_VERSION, SessionStorageReloadMemory()),
      versionCheck = { contentService.versionCheck(it) },
      modalOpen = { Modal.getAllModals().any { it.visible } },
      writingReason = { writingReason },
      ui = KVisionVersionCheckUi,
    ).run()
}

/** What the version check shows and does in the page: [KVisionVersionCheckUi] in the app, a fake in a test. */
internal interface VersionCheckUi {
  /** Shows the update dialog with [response]'s title and message; [onClosed] runs once, when the student closes it. */
  fun showDialog(
    response: VersionCheckResponse,
    onClosed: () -> Unit,
  )

  /** Shows one toast of the countdown, with [secondsLeft] in place of `{{COUNT}}` in [response]'s message. */
  fun showCountdown(
    response: VersionCheckResponse,
    secondsLeft: Int,
  )

  /** Reloads the page. */
  fun reload()
}

/**
 * Runs the version check: asks the server ([versionCheck]) what it makes of [state]'s request, acts on the answer as
 * [VersionCheckState.decide] says through [ui], waits [VersionCheckState.checkInterval], and goes again until the page
 * reloads. Everything it touches is passed in, so a test can drive it with fakes and virtual time.
 *
 * @param modalOpen whether any modal is open (see [VersionCheckState.decide])
 * @param writingReason whether the student is giving a reason (MainPanel's `writingReason`)
 */
internal class VersionCheckLoop(
  private val state: VersionCheckState,
  private val versionCheck: suspend (VersionCheckInfo) -> VersionCheckResponse,
  private val modalOpen: () -> Boolean,
  private val writingReason: () -> Boolean,
  private val ui: VersionCheckUi,
) {
  /** Checks, and waits between checks, until the page reloads itself. */
  suspend fun run() {
    while (true) {
      try {
        if (checkOnce()) return
      } catch (e: CancellationException) {
        throw e
      } catch (e: SecurityException) {
        // Signed out: the sign-in dialog handles that, and the next check tries again
      } catch (e: Throwable) {
        console.log("Version check failed: $e")
      }
      delay(state.checkInterval)
    }
  }

  /** One check: asks the server and acts on its answer. True once the page is reloading. */
  suspend fun checkOnce(): Boolean {
    val response = versionCheck(state.request())
    return when (state.decide(response, modalOpen(), writingReason())) {
      SHOW_DIALOG -> {
        ui.showDialog(response) { state.dialogClosed() }
        false
      }

      COUNT_DOWN -> {
        countDownAndReload(response)
      }

      else -> {
        false
      }
    }
  }

  /**
   * Counts down, a toast a second, then reloads. The choice buttons stay usable under the toasts, so if the student
   * starts a reason meanwhile, the countdown stops and the reload is left to a later check.
   */
  private suspend fun countDownAndReload(response: VersionCheckResponse): Boolean {
    val seconds = (response.toastConfig?.duration ?: 5.seconds).inWholeSeconds.toInt()
    for (secondsLeft in seconds downTo 1) {
      if (writingReason()) return false
      ui.showCountdown(response, secondsLeft)
      delay(1.seconds)
    }
    if (!state.reloadNow(writingReason())) return false
    ui.reload()
    return true
  }
}

/**
 * [ReloadMemory] in the tab's `sessionStorage` under [key]: it outlives a reload but not the tab. Where the browser
 * refuses storage (blocked, or full) it remembers nothing, and the page reloads itself for every mismatch, as it did
 * before there was a memory.
 */
internal class SessionStorageReloadMemory(
  private val key: String = KEY,
) : ReloadMemory {
  override var reloadedFor: String?
    get() =
      try {
        window.sessionStorage.getItem(key)
      } catch (e: Throwable) {
        null
      }
    set(value) {
      try {
        if (value == null) window.sessionStorage.removeItem(key) else window.sessionStorage.setItem(key, value)
      } catch (e: Throwable) {
        console.log("Could not keep the reloaded apiVersion in sessionStorage: $e")
      }
    }

  companion object {
    const val KEY = "history-walk.reloaded-for-api-version"
  }
}

/** The version check's dialog, countdown toasts, and reload, in KVision. */
internal object KVisionVersionCheckUi : VersionCheckUi {
  override fun showDialog(
    response: VersionCheckResponse,
    onClosed: () -> Unit,
  ) {
    var openAlert: Alert? = null
    val alert =
      Alert(
        caption = response.title,
        text = response.message,
        rich = response.dialogConfig?.rich ?: false,
        size = response.dialogConfig?.size.toModalSize(),
        centered = response.dialogConfig?.centered ?: false,
      ) {
        // Alert calls this from every hide(), its constructor's included, so only the first after show() counts.
        // Disposed then, or KVision's list of modals keeps every update dialog for good.
        openAlert?.also {
          openAlert = null
          it.dispose()
          onClosed()
        }
      }
    openAlert = alert
    alert.show()
  }

  override fun showCountdown(
    response: VersionCheckResponse,
    secondsLeft: Int,
  ) {
    toastContainer.showToast(
      message = response.message.replace("{{COUNT}}", secondsLeft.toString()),
      title = response.title,
      color = response.toastConfig?.color.toBsColor(),
      bgColor = response.toastConfig?.bgColor.toBsBgColor(),
      autohide = response.toastConfig?.autohide ?: true,
      delay = 725,
    )
  }

  // The server keeps the student's place, so the walk picks up where it left off
  override fun reload() {
    window.location.href = "/"
  }

  private fun String?.toBsColor() = BsColor.entries.firstOrNull { it.name == this }

  private fun String?.toBsBgColor() = BsBgColor.entries.firstOrNull { it.name == this }

  private fun String?.toModalSize() = ModalSize.entries.firstOrNull { it.name == this }
}
