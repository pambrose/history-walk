package com.pambrose.historywalk

import com.pambrose.BuildConfig
import com.pambrose.historywalk.AlertLevel.DIALOG
import com.pambrose.historywalk.AlertLevel.NONE
import com.pambrose.historywalk.AlertLevel.RELOAD
import com.pambrose.historywalk.ClientUtils.toastContainer
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Asks the server, every [versionCheckInterval], whether this page's bundle is still current (ServerVersionCheck), so
 * a page left open across a release that changes `apiVersion` is asked to reload, then reloads itself. The check
 * goes around `withAuth`: a signed-out page is left to the sign-in dialog, and its failed checks are retried quietly.
 */
object ClientVersionCheck {
  private val contentService = getService<IContentService>()

  // The server's answers set these: the next wait, and the first warning's time, which goes back with the next check
  private var versionCheckInterval: Duration = 60.seconds
  private var reloadWarningInstant: Instant? = null

  suspend fun checkClientVersion() {
    var dialogDisplayed = false

    while (true) {
      try {
        val response =
          contentService.versionCheck(
            VersionCheckInfo(BuildConfig.VERSION, BuildConfig.API_VERSION, reloadWarningInstant),
          )
        reloadWarningInstant = response.reloadWarningInstant
        response.versionCheckInterval?.also { versionCheckInterval = it }

        when (response.alertLevel) {
          NONE -> {}

          DIALOG -> {
            // Held back while another modal, such as the sign-in dialog, is open; a later check shows it
            if (!dialogDisplayed && Modal.getAllModals().none { it.visible }) {
              dialogDisplayed = true
              Alert.show(
                caption = response.title,
                text = response.message,
                rich = response.dialogConfig?.rich ?: false,
                size = response.dialogConfig?.size.toModalSize(),
                centered = response.dialogConfig?.centered ?: false,
              ) { dialogDisplayed = false }
            }
          }

          RELOAD -> {
            for (sec in (response.toastConfig?.duration ?: 5.seconds).inWholeSeconds downTo 1) {
              showToast(sec.toInt(), response)
              delay(1.seconds)
            }
            // The server keeps the user's place, so the walk picks up where it left off
            window.location.href = "/"
            return
          }
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: SecurityException) {
        // Signed out: the sign-in dialog handles that, and the next check tries again
      } catch (e: Throwable) {
        console.log("Version check failed: $e")
      }
      delay(versionCheckInterval)
    }
  }

  private fun showToast(
    sec: Int,
    response: VersionCheckResponse,
  ) {
    toastContainer.showToast(
      message = response.message.replace("{{COUNT}}", sec.toString()),
      title = response.title,
      color = response.toastConfig?.color.toBsColor(),
      bgColor = response.toastConfig?.bgColor.toBsBgColor(),
      autohide = response.toastConfig?.autohide ?: true,
      delay = 725,
    )
  }

  private fun String?.toBsColor() = BsColor.entries.firstOrNull { it.name == this }

  private fun String?.toBsBgColor() = BsBgColor.entries.firstOrNull { it.name == this }

  private fun String?.toModalSize() = ModalSize.entries.firstOrNull { it.name == this }
}
