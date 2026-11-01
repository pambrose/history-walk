package com.pambrose.historywalk

import com.pambrose.BuildConfig
import com.pambrose.historywalk.AlertLevel.DIALOG
import com.pambrose.historywalk.AlertLevel.NONE
import com.pambrose.historywalk.AlertLevel.RELOAD
import com.pambrose.historywalk.EnvVar.CLIENT_RELOAD_WARNING_INTERVAL_SECS
import com.pambrose.historywalk.EnvVar.CLIENT_VERSION_CHECK_INTERVAL_SECS
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Tells an open page whether its bundle still matches the server. A page whose bundle has another `apiVersion`
 * (gradle.properties) than the server's is first shown a dialog with a reload link, then, once
 * [reloadWarningInterval] has passed since that first warning, a countdown toast before it reloads itself. The
 * reload gets the new bundle because the client's files are checked with the server on every load
 * (`Routes.staticContent`).
 */
object ServerVersionCheck {
  /** How often a page checks while its version matches (CLIENT_VERSION_CHECK_INTERVAL_SECS, default 60). */
  val versionCheckInterval: Duration by lazy { CLIENT_VERSION_CHECK_INTERVAL_SECS.getEnv(60).seconds }

  /** How long the reload dialog is offered before the page reloads itself (CLIENT_RELOAD_WARNING_INTERVAL_SECS, 60). */
  val reloadWarningInterval: Duration by lazy { CLIENT_RELOAD_WARNING_INTERVAL_SECS.getEnv(60).seconds }

  /** How often a page that has been warned checks, so the dialog turns into the countdown soon after it is due. */
  internal val WARNED_CHECK_INTERVAL = 15.seconds

  /** How long the countdown toast counts down before the page reloads. */
  internal val RELOAD_COUNTDOWN = 15.seconds

  internal const val TITLE = "Application Update"

  fun checkServerVersion(
    client: VersionCheckInfo,
    serverApiVersion: String = BuildConfig.API_VERSION,
    warningInterval: Duration = reloadWarningInterval,
    checkInterval: Duration = versionCheckInterval,
    now: Instant = Clock.System.now(),
  ): VersionCheckResponse {
    val warnedAt = client.reloadWarningInstant ?: now
    return when {
      client.apiVersion == serverApiVersion -> {
        VersionCheckResponse(NONE, versionCheckInterval = checkInterval)
      }

      // The page is first asked, in a dialog, to reload; the warning starts the first time it is shown
      now - warnedAt < warningInterval -> {
        VersionCheckResponse(
          DIALOG,
          title = TITLE,
          message = "A new version of History Walk is available. " +
            "Please <a href='/'>click here</a> to reload this page and continue.",
          dialogConfig = DialogConfig(rich = true, size = "LARGE"),
          reloadWarningInstant = warnedAt,
          versionCheckInterval = WARNED_CHECK_INTERVAL,
        )
      }

      // Then a countdown toast, after which the page reloads itself
      else -> {
        VersionCheckResponse(
          RELOAD,
          title = TITLE,
          message = "A new version of History Walk is available. Reloading this page in {{COUNT}} seconds...",
          toastConfig = ToastConfig(color = "WHITE", bgColor = "DANGER", autohide = true, duration = RELOAD_COUNTDOWN),
        )
      }
    }
  }
}
