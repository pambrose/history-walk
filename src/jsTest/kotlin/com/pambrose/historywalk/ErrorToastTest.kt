package com.pambrose.historywalk

import com.pambrose.historywalk.ClientUtils.CLICK_FAILED
import com.pambrose.historywalk.ClientUtils.PAGE_NOT_LOADED
import com.pambrose.historywalk.ClientUtils.launchOrErrorToast
import com.pambrose.historywalk.ClientUtils.orErrorToast
import dev.kilua.rpc.SecurityException
import dev.kilua.rpc.ServiceException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive

/**
 * Wave 2's error handling (issue #1): a failed call shows a toast and leaves the page usable, while a 401 goes on to
 * the sign-in handling and a server refusal goes on to the click handler that made it.
 */
class ErrorToastTest : StringSpec() {
  init {
    timeout = CLIENT_TEST_TIMEOUT_MS

    "a call that succeeds returns its result and shows no toast" {
      var result: String? = null

      toastsShownBy { result = orErrorToast(CLICK_FAILED) { "the next slide" } }.shouldBeEmpty()

      result shouldBe "the next slide"
    }

    "a call that fails shows the message in a toast and returns null" {
      var result: String? = "unset"

      toastsShownBy {
        result = orErrorToast<String>(CLICK_FAILED) { throw IllegalStateException("Service Unavailable") }
      } shouldBe listOf(CLICK_FAILED)

      result.shouldBeNull()
    }

    "a SecurityException is passed on to the sign-in handling, not toasted" {
      toastsShownBy {
        shouldThrow<SecurityException> {
          orErrorToast<String>(CLICK_FAILED) { throw SecurityException("Unauthorized") }
        }
      }.shouldBeEmpty()
    }

    "a ServiceException is passed on to the click handler, not toasted" {
      toastsShownBy {
        shouldThrow<ServiceException> {
          orErrorToast<String>(CLICK_FAILED) { throw ServiceException("You are not on that slide") }
        }.message shouldBe "You are not on that slide"
      }.shouldBeEmpty()
    }

    "a cancellation is passed on, not toasted" {
      toastsShownBy {
        shouldThrow<CancellationException> {
          orErrorToast<String>(CLICK_FAILED) { throw CancellationException("the page moved on") }
        }
      }.shouldBeEmpty()
    }

    "a launch that fails shows a toast, and later launches still run" {
      toastsShownBy {
        launchOrErrorToast(PAGE_NOT_LOADED) { error("Bad Gateway") }.join()
      } shouldBe listOf(PAGE_NOT_LOADED)

      var ran = false
      launchOrErrorToast(PAGE_NOT_LOADED) { ran = true }.join()

      ran shouldBe true
      AppScope.isActive shouldBe true
    }

    "a refusal that escapes its launch fails only that launch, not the scope the later clicks run in" {
      val refused = launchOrErrorToast(CLICK_FAILED) { throw ServiceException("refused") }
      refused.join()

      refused.isCancelled shouldBe true
      AppScope.isActive shouldBe true

      var ran = false
      launchOrErrorToast(CLICK_FAILED) { ran = true }.join()
      ran shouldBe true
    }
  }
}
