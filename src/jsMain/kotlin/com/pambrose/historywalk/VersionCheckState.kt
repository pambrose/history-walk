package com.pambrose.historywalk

import com.pambrose.historywalk.VersionCheckAction.COUNT_DOWN
import com.pambrose.historywalk.VersionCheckAction.HOLD_DIALOG
import com.pambrose.historywalk.VersionCheckAction.PUT_OFF_RELOAD
import com.pambrose.historywalk.VersionCheckAction.SHOW_DIALOG
import com.pambrose.historywalk.VersionCheckAction.UP_TO_DATE
import com.pambrose.historywalk.VersionCheckAction.WAIT
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Remembers, across the page's own reloads within one browser tab, the `apiVersion` of the bundle that last reloaded
 * itself because the server said it was out of date. [VersionCheckState] reads it to stop a reload loop while a stale
 * bundle is still served.
 */
internal interface ReloadMemory {
  /** The `apiVersion` (BuildConfig.API_VERSION) of the bundle that last reloaded itself in this tab, or null. */
  var reloadedFor: String?
}

/** What the page does with one answer from the server's version check, as [VersionCheckState.decide] says. */
internal enum class VersionCheckAction {
  /** The bundle matches the server's (AlertLevel.NONE): nothing to show. */
  UP_TO_DATE,

  /** Show the update dialog now, with the answer's title and message; the warning time starts with it. */
  SHOW_DIALOG,

  /**
   * Nothing new to show: the update dialog is already open, or, on a page whose own reload brought back the same
   * bundle, it has already been shown once.
   */
  WAIT,

  /**
   * The update dialog is held back because another modal is open or the student is writing a reason. The warning
   * time does not start, so the dialog, shown by a later check, still gets all of it before the page reloads.
   */
  HOLD_DIALOG,

  /** The warning time is up: count down in a toast, then reload if [VersionCheckState.reloadNow] agrees. */
  COUNT_DOWN,

  /** The warning time is up, but the student is writing a reason, which reloading loses; a later check asks again. */
  PUT_OFF_RELOAD,
}

/**
 * The page's side of the version check (ServerVersionCheck is the server's): what it sends, and what it makes of each
 * answer. It touches no DOM, so a test can drive it with any [ReloadMemory] and answers of its own.
 *
 * The server measures the warning time from [reloadWarningInstant], which it stamps on its first DIALOG answer and the
 * page sends back with each later check; once that time is up, it answers RELOAD. So:
 * - The page keeps the stamp only from the answer whose dialog it actually shows. A dialog held back, by the reason
 *   dialog say, starts no clock, so no reload can come without the student having seen the dialog first.
 * - A RELOAD is put off while the student is writing a reason, and the stamp kept, so the next check asks again.
 * - Before reloading, the page records its bundle's `apiVersion` in [reloadMemory]. If the reload brings back the same
 *   bundle, as when Cloudflare still serves the old `main.bundle.js`, the page sends no stamp at all, so the server
 *   keeps answering DIALOG: the dialog, with its reload link, is shown once, and the page never reloads itself again
 *   for that bundle. An up-to-date answer clears the record.
 *
 * @param bundleVersion this bundle's version (BuildConfig.VERSION)
 * @param bundleApiVersion this bundle's `apiVersion` (BuildConfig.API_VERSION), which the server compares with its own
 */
internal class VersionCheckState(
  private val bundleVersion: String,
  private val bundleApiVersion: String,
  private val reloadMemory: ReloadMemory,
) {
  /** How long to wait before the next check: the server's last say, or [DEFAULT_CHECK_INTERVAL] until it has one. */
  var checkInterval: Duration = DEFAULT_CHECK_INTERVAL
    private set

  /**
   * When the student was first shown the update dialog, as the server stamped it; sent back with every check. Null
   * until the dialog has really been shown, and always null on a page whose reload did not help ([reloadDidNotHelp]).
   */
  var reloadWarningInstant: Instant? = null
    private set

  /** Whether the update dialog is open: from a [SHOW_DIALOG] until [dialogClosed]. */
  var dialogOpen = false
    private set

  /** Whether the update dialog has been shown at all on this page. */
  var dialogShown = false
    private set

  /**
   * True when this bundle has already reloaded itself in this tab for a mismatch that is still there: the reload
   * brought back the same bundle, so another one would bring it back again.
   */
  val reloadDidNotHelp: Boolean
    get() = reloadMemory.reloadedFor == bundleApiVersion

  /** What the next check sends the server. */
  fun request(): VersionCheckInfo = VersionCheckInfo(bundleVersion, bundleApiVersion, reloadWarningInstant)

  /**
   * Decides what to do with the server's [response], and keeps the state that goes with it.
   *
   * @param modalOpen whether any modal is open, such as the sign-in or the reason dialog; the update dialog's own state
   *   is kept here ([dialogOpen]), and comes first
   * @param writingReason whether the student is giving a reason (MainPanel's `writingReason`), which reloading loses
   */
  fun decide(
    response: VersionCheckResponse,
    modalOpen: Boolean,
    writingReason: Boolean,
  ): VersionCheckAction {
    response.versionCheckInterval?.also { checkInterval = it }
    return when (response.alertLevel) {
      AlertLevel.NONE -> {
        // Current again, after a reload that worked or a server that went back: forget the warning and the reload
        reloadWarningInstant = null
        if (reloadMemory.reloadedFor != null) reloadMemory.reloadedFor = null
        UP_TO_DATE
      }

      AlertLevel.DIALOG -> {
        when {
          dialogOpen -> {
            WAIT
          }

          // The reload link would bring back this same bundle, so the dialog is not shown again and again
          reloadDidNotHelp && dialogShown -> {
            WAIT
          }

          modalOpen || writingReason -> {
            HOLD_DIALOG
          }

          else -> {
            dialogOpen = true
            dialogShown = true
            // The warning starts now that the student sees it. A dialog shown again after being closed keeps the
            // first stamp, and a page whose reload did not help keeps none, so the server never answers RELOAD.
            if (reloadWarningInstant == null && !reloadDidNotHelp) reloadWarningInstant = response.reloadWarningInstant
            SHOW_DIALOG
          }
        }
      }

      AlertLevel.RELOAD -> {
        when {
          // Not expected, as such a page sends no stamp; dropping the stamp brings the DIALOG answers back
          reloadDidNotHelp -> {
            reloadWarningInstant = null
            WAIT
          }

          writingReason -> {
            PUT_OFF_RELOAD
          }

          else -> {
            COUNT_DOWN
          }
        }
      }
    }
  }

  /** The student closed the update dialog; a later DIALOG answer may show it again (see [decide]). */
  fun dialogClosed() {
    dialogOpen = false
  }

  /**
   * Asked once a [COUNT_DOWN] has run out: true if the page should reload now, in which case this bundle's
   * `apiVersion` is recorded first ([reloadDidNotHelp]). False if the student has started a reason since: the reload
   * is put off, and as the stamp is kept, the next check answers RELOAD again.
   */
  fun reloadNow(writingReason: Boolean): Boolean {
    if (writingReason) return false
    reloadMemory.reloadedFor = bundleApiVersion
    return true
  }

  companion object {
    /** How long the page waits between checks until the server says otherwise. */
    val DEFAULT_CHECK_INTERVAL = 60.seconds
  }
}
