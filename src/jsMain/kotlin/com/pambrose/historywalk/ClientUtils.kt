package com.pambrose.historywalk

import dev.kilua.rpc.SecurityException
import dev.kilua.rpc.ServiceException
import io.kvision.core.BsBgColor
import io.kvision.core.BsColor
import io.kvision.core.CssSize
import io.kvision.core.StyledComponent
import io.kvision.core.TextTransform
import io.kvision.html.Button
import io.kvision.toast.ToastContainer
import io.kvision.toast.ToastContainerPosition.BOTTOMRIGHT
import kotlinx.browser.document
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLFormElement

object ClientUtils {
  // What a student is told when something fails; the page stays as it was, so trying again is safe
  const val PAGE_NOT_LOADED = "Something went wrong loading the page. Please reload it."
  const val CLICK_FAILED = "Something went wrong, so nothing changed. Please try again."
  const val REASON_NOT_SAVED = "Something went wrong, so your reason was not saved. Please click OK again."

  /** The page's one toast container, shared by the error toasts and the version check's countdown. */
  val toastContainer by lazy { ToastContainer(BOTTOMRIGHT) }

  fun Button.lowercase() {
    textTransform = TextTransform.NONE
  }

  fun StyledComponent.verticalPadding(size: CssSize) {
    paddingTop = size
    paddingBottom = size
  }

  suspend fun <T> withAuth(block: suspend () -> T) =
    Security.withAuth {
      try {
        block()
      } catch (e: Exception) {
        // A SecurityException just means "not signed in"; Security.afterError logs that.
        if (e !is SecurityException) console.log("Request failed: $e")
        throw e
      }
    }

  fun showErrorToast(message: String) =
    toastContainer.showToast(message, color = BsColor.DANGEREMPHASIS, bgColor = BsBgColor.DANGERSUBTLE)

  /**
   * Runs [block] and returns its result. If it fails, shows [failureMessage] in a toast and returns null, so the caller
   * can leave the page as it was and the student can try again.
   *
   * Call it around the RPC calls, never inside [withAuth]'s block: a 401 must reach `Security.withAuth`, which shows
   * the sign-in dialog and parks the call for good, so a sign-in never gets here.
   */
  suspend fun <T : Any> orErrorToast(
    failureMessage: String,
    block: suspend () -> T,
  ): T? =
    try {
      block()
    } catch (e: CancellationException) {
      throw e
    } catch (e: SecurityException) {
      // Not a failure to report: sign-in is Security's job (see above). The scope's handler logs it.
      throw e
    } catch (e: ServiceException) {
      // The server refused the request and said why (ContentService's checks), so retrying it will not help. The click
      // handler that made it shows the message and the student's real slide (MainPanel's onClickWhenIdle).
      throw e
    } catch (e: Throwable) {
      console.log("Showing \"$failureMessage\" after: $e")
      showErrorToast(failureMessage)
      null
    }

  /** Launches [block] in [AppScope]; a failure shows [failureMessage] in a toast (see [orErrorToast]). */
  fun launchOrErrorToast(
    failureMessage: String,
    block: suspend () -> Unit,
  ): Job = AppScope.launch { orErrorToast(failureMessage, block) }

  /** Submits a POST (no fields) to [path], e.g. for logout, which a plain link (a GET) cannot do. */
  fun postTo(path: String) {
    val form = document.createElement("form") as HTMLFormElement
    form.method = "post"
    form.action = path
    document.body?.appendChild(form)
    form.submit()
  }
}
