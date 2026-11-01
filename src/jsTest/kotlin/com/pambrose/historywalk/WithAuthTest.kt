package com.pambrose.historywalk

import dev.kilua.rpc.SecurityException
import dev.kilua.rpc.ServiceException
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldNotBeInstanceOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * `ClientUtils.withAuth`, through `Rpc` and [FakeServer] (issue #18): only a 401 reaches KVision's sign-in handling,
 * which shows the sign-in dialog and parks the call; any other failure reaches the caller as itself, even while KVision
 * counts the page as signed out, as it does before the page's first successful call.
 *
 * The page has one `Security`, whose sign-in prompt shows once and whose parked call never ends, so this is the only
 * spec that sends a 401 through it, and it runs last (Kotest runs specs in name order). The tests run in order: the
 * first two find the page signed in by the specs before, the third signs it out, and the last runs signed out.
 */
class WithAuthTest : StringSpec() {
  // A parked call never finishes, so the calls run outside the test's own scope, which would wait for them
  private val outside = CoroutineScope(Dispatchers.Default)

  private fun signInDialogShowing() = visibleModals().any { it is SignInDialog }

  /** Makes [call] outside the test, and returns how it ended, or null if it was still running after a second. */
  private suspend fun <T> outcomeOf(call: suspend () -> T): Result<T>? {
    val outcome = CompletableDeferred<Result<T>>()
    outside.launch { outcome.complete(runCatching { call() }) }
    return withTimeoutOrNull(1.seconds) { outcome.await() }
  }

  init {
    timeout = CLIENT_TEST_TIMEOUT_MS

    // The page first: the sign-in dialog draws through its Root
    beforeSpec { TestPage.root }
    beforeTest { FakeServer.reset() }

    "a server error reaches the caller as itself, with no sign-in dialog" {
      FakeServer.on(IContentService::getCurrentSlide) { Reply.HttpError(503, "Service Unavailable") }

      val failure = outcomeOf { Rpc.getCurrentSlide() }.shouldNotBeNull().exceptionOrNull()

      failure.shouldBeInstanceOf<Exception>()
      failure.shouldNotBeInstanceOf<SecurityException>()
      failure.message shouldBe "Service Unavailable"
      signInDialogShowing() shouldBe false
    }

    "a refused move reaches the caller as its ServiceException, with no sign-in dialog" {
      FakeServer.on(IContentService::goBackInTime) { Reply.Refused("You cannot go back there") }

      val failure = outcomeOf { Rpc.goBackInTime(ParentTitle("/Start", "Start")) }.shouldNotBeNull().exceptionOrNull()

      failure.shouldBeInstanceOf<ServiceException>()
      failure.message shouldBe "You cannot go back there"
      signInDialogShowing() shouldBe false
    }

    "a 401 shows the sign-in dialog, and the call waits behind it for good" {
      FakeServer.on(IContentService::getCurrentSlide) { Reply.HttpError(401, "Unauthorized") }

      outcomeOf { Rpc.getCurrentSlide() } shouldBe null

      awaitUntil { signInDialogShowing() }
      delay(50.milliseconds)
      FakeServer.callsTo(IContentService::getCurrentSlide) shouldHaveSize 1
    }

    "while the page counts as signed out, a failed call still reaches the caller, not the sign-in handling" {
      // KVision took the 401 for a sign-out, as it takes any failure before the first call succeeds. A failure that
      // reached its sign-in handling now would park behind the prompt already shown, and never come back.
      closeModals()
      FakeServer.on(IContentService::getCurrentSlide) { error("The network is down") }

      // KVision's own withAuth, not ClientUtils', would park this failure: the page really is signed out
      outcomeOf { Security.withAuth { error("The network is down") } } shouldBe null

      val failure = outcomeOf { Rpc.getCurrentSlide() }.shouldNotBeNull().exceptionOrNull()

      failure.shouldBeInstanceOf<Exception>()
      failure.shouldNotBeInstanceOf<SecurityException>()
      failure.message.orEmpty() shouldContain "The network is down"
      signInDialogShowing() shouldBe false
    }
  }
}
