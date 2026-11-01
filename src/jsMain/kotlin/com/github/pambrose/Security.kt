package com.github.pambrose

import io.kvision.modal.Modal
import io.kvision.remote.SecurityMgr
import io.kvision.state.ObservableValue
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext

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

object Security : SecurityMgr() {
  /** Why the last sign-in was refused (from the `?error=` redirect), or null. */
  val signInError = ObservableValue<String?>(null)

  private val signInDialog = SignInDialog(signInError)
  private val loginPrompt =
    LoginPrompt {
      Modal.getAllModals().filter { it.visible && it !== signInDialog }.forEach { it.hide() }
      signInDialog.getResult()
    }

  override suspend fun login(): Boolean = loginPrompt.showOnceAndPark()

  override suspend fun afterLogin() {
    console.log("Signed in")
  }

  override suspend fun afterError() {
    console.log("Sign-in required: showing the sign-in dialog")
  }
}
