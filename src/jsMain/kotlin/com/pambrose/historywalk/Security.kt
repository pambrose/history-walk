package com.pambrose.historywalk

import io.kvision.modal.Modal
import io.kvision.remote.SecurityMgr
import io.kvision.state.ObservableValue
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Shows the sign-in prompt once for the life of the page, then parks for good.
 *
 * `SecurityMgr.withAuth` calls `login()` from a `while (!isLoggedIn)` loop that swallows every `Throwable`, and
 * nothing outside that loop can end it. So the prompt is shown once rather than once per turn of the loop, and
 * [showOnceAndPark] must never return, cancellation included: a cancelled coroutine resumes every suspension at once,
 * so a park that let cancellation through would spin that loop and reopen the dialog each time round.
 */
internal class LoginPrompt(
  private val show: suspend () -> Unit,
) {
  private var shown = false

  suspend fun showOnceAndPark(): Nothing {
    if (!shown) {
      shown = true
      show()
    }
    // NonCancellable, so the wait outlives the cancellation of whichever call triggered sign-in. The page
    // navigates away once "Sign in with Google" is clicked, which is what ends the wait in practice.
    withContext(NonCancellable) { awaitCancellation() }
  }
}

/**
 * The page's KVision security manager: a 401 calls [login], which shows the sign-in dialog once, with the server's
 * greeting and domain, and parks for good (see [LoginPrompt]).
 */
object Security : SecurityMgr() {
  /** Why the last sign-in was refused (from the `?error=` redirect), or null. */
  val signInError = ObservableValue<SignInRefusal?>(null)

  /** The domain whose accounts may sign in, as the server has it (`SCHOOL_EMAIL_DOMAIN`), which the dialog names. */
  val schoolDomain = ObservableValue(School.DEFAULT_DOMAIN)

  private val signInDialog = SignInDialog(signInError, schoolDomain)
  private val loginPrompt =
    LoginPrompt {
      Modal.getAllModals().filter { it.visible && it !== signInDialog }.forEach { it.hide() }
      SignInBackground.show()
      // NonCancellable, so cancelling the call that triggered sign-in cannot stop the dialog from opening; the timeout
      // keeps a slow server from holding it back, in which case the default title and domain stay.
      withContext(NonCancellable) {
        withTimeoutOrNull(3.seconds) {
          coroutineScope {
            val greeting = async { fetchLoginGreeting() }
            val domain = async { fetchSchoolDomain() }
            greeting.await()?.let { signInDialog.caption = it }
            domain.await()?.let { schoolDomain.value = it }
          }
        }
      }
      signInDialog.getResult()
    }

  override suspend fun login(): Boolean = loginPrompt.showOnceAndPark()

  // No afterLogin: SecurityMgr calls it after the first call that returns, and ClientUtils.withAuth returns a failure
  // too (in a Result), so a "Signed in" there could follow a failed call

  override suspend fun afterError() {
    console.log("Sign-in required: showing the sign-in dialog")
  }
}
