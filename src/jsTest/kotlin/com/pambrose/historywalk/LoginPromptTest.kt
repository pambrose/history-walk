package com.pambrose.historywalk

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * `SecurityMgr.withAuth` calls `login()` from a loop that swallows every Throwable, so [LoginPrompt] must show the
 * sign-in dialog once and then never return, not even when the call that triggered it is cancelled.
 */
class LoginPromptTest : StringSpec() {
  // The parked calls never finish, so they run outside the test's own scope, which would wait for them
  private val outside = CoroutineScope(Dispatchers.Default)

  // Long enough for every launched call to reach its park
  private suspend fun settle() = delay(50.milliseconds)

  init {
    timeout = CLIENT_TEST_TIMEOUT_MS

    "the first call shows the prompt, then parks instead of returning" {
      var shows = 0
      val prompt = LoginPrompt { shows++ }

      val call = outside.launch { prompt.showOnceAndPark() }
      settle()

      shows shouldBe 1
      call.isActive shouldBe true
    }

    "later calls park without showing the prompt again" {
      var shows = 0
      val prompt = LoginPrompt { shows++ }

      val calls = List(3) { outside.launch { prompt.showOnceAndPark() } }
      settle()

      shows shouldBe 1
      calls.all { it.isActive } shouldBe true
    }

    "cancelling the call that triggered sign-in does not end the park" {
      var shows = 0
      val prompt = LoginPrompt { shows++ }

      val call = outside.launch { prompt.showOnceAndPark() }
      settle()
      call.cancel()
      settle()

      call.isCompleted shouldBe false
      shows shouldBe 1
    }

    "SecurityMgr's sign-in loop, once its call is cancelled, does not spin and reopen the prompt" {
      var shows = 0
      val prompt = LoginPrompt { shows++ }
      var logins = 0

      // The loop in SecurityMgr.withAuth, bounded: a park that let cancellation through would spin it without ever
      // suspending, which would hang the page rather than fail the spec
      val call: Job =
        outside.launch {
          while (logins < 100) {
            logins++
            try {
              prompt.showOnceAndPark()
            } catch (e: Throwable) {
              console.log("login() threw $e")
            }
          }
        }
      settle()
      call.cancel()
      settle()

      logins shouldBe 1
      shows shouldBe 1
      call.isCompleted shouldBe false
    }
  }
}
