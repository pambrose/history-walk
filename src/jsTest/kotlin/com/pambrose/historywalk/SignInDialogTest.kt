package com.pambrose.historywalk

import com.pambrose.historywalk.Auth.DEFAULT_LOGIN_GREETING
import com.pambrose.historywalk.EndPoints.AUTH_LOGIN
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kvision.state.ObservableValue
import kotlinx.coroutines.delay
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.KeyboardEventInit
import kotlin.time.Duration.Companion.milliseconds

/** Checks [SignInDialog]: its sign-in link, title, domain, and refusal message, and that it cannot be dismissed. */
class SignInDialogTest : StringSpec() {
  private val opened = mutableListOf<SignInDialog>()

  // Each spec's dialog, shown on the page; a test passes its own navigate, so nothing leaves the page
  private suspend fun signInDialog(
    signInError: ObservableValue<SignInRefusal?> = ObservableValue(null),
    schoolDomain: ObservableValue<String> = ObservableValue(School.DEFAULT_DOMAIN),
    navigate: (String) -> Unit = {},
  ): SignInDialog =
    SignInDialog(signInError, schoolDomain, navigate).also {
      opened += it
      showModal(it)
    }

  init {
    timeout = CLIENT_TEST_TIMEOUT_MS

    afterTest {
      closeModals()
      opened.forEach { it.dispose() }
      opened.clear()
    }

    "Sign in with Google goes to the sign-in route" {
      val visited = mutableListOf<String>()
      val dialog = signInDialog(navigate = { visited += it })

      dialog.element.button("Sign in with Google").click()

      visited shouldBe listOf("/$AUTH_LOGIN")
    }

    "the dialog is titled with the default greeting until the server's arrives" {
      val dialog = signInDialog()

      dialog.caption shouldBe DEFAULT_LOGIN_GREETING
      dialog.element.textContent.orEmpty() shouldContain "Sign in with your athenian.org Google account"
    }

    "after a refused sign-in the dialog says why, and says nothing once the reason is cleared" {
      val why = "jsmith@gmail.com isn't an athenian.org account. Please sign in with your school account."
      val signInError = ObservableValue<SignInRefusal?>(SignInRefusal(SignInError.WRONG_DOMAIN, "jsmith@gmail.com"))
      val dialog = signInDialog(signInError)

      dialog.element.textContent.orEmpty() shouldContain why

      signInError.value = null
      dialog.element.textContent.orEmpty() shouldNotContain why
    }

    "the dialog names the school's domain as the server has it, in its instruction and in a refusal" {
      val schoolDomain = ObservableValue(School.DEFAULT_DOMAIN)
      val signInError = ObservableValue<SignInRefusal?>(SignInRefusal(SignInError.WRONG_DOMAIN, "jsmith@gmail.com"))
      val dialog = signInDialog(signInError, schoolDomain)

      schoolDomain.value = "example.edu"

      dialog.element.textContent.orEmpty().run {
        this shouldContain "Sign in with your example.edu Google account"
        this shouldContain "jsmith@gmail.com isn't an example.edu account."
        this shouldNotContain "athenian.org"
      }
    }

    "the dialog cannot be dismissed: it has no close button, and Escape leaves it open" {
      val dialog = signInDialog()

      dialog.closeButton shouldBe false
      dialog.element.dispatchEvent(KeyboardEvent("keydown", KeyboardEventInit(key = "Escape", bubbles = true)))
      delay(50.milliseconds)

      dialog.visible shouldBe true
      visibleModals() shouldBe listOf(dialog)
    }
  }
}
