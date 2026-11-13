package com.pambrose.historywalk

import com.pambrose.historywalk.Auth.DEFAULT_LOGIN_GREETING
import com.pambrose.historywalk.EndPoints.AUTH_DOMAIN
import com.pambrose.historywalk.EndPoints.AUTH_GREETING
import com.pambrose.historywalk.EndPoints.AUTH_LOGIN
import io.kvision.core.Col
import io.kvision.core.Color
import io.kvision.html.Button
import io.kvision.html.ButtonStyle.PRIMARY
import io.kvision.html.p
import io.kvision.html.span
import io.kvision.modal.Dialog
import io.kvision.state.ObservableValue
import io.kvision.state.bind
import kotlinx.browser.window
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.await

/**
 * The text the server answers at [path], trimmed, or null when it cannot be fetched or is blank, in which case the
 * dialog keeps its default.
 */
private suspend fun fetchText(path: String): String? =
  try {
    val response = window.fetch("/$path").await()
    if (response.ok)
      response
      .text()
      .await()
      .trim()
      .takeIf { it.isNotEmpty() }
      else
      null
  } catch (e: CancellationException) {
    throw e
  } catch (e: Throwable) {
    console.log("Could not fetch /$path: $e")
    null
  }

/** The sign-in dialog's title from the server (LOGIN_GREETING, served at [AUTH_GREETING]), or null. */
suspend fun fetchLoginGreeting(): String? = fetchText(AUTH_GREETING)

/** The domain whose accounts may sign in (SCHOOL_EMAIL_DOMAIN, served at [AUTH_DOMAIN]), or null. */
suspend fun fetchSchoolDomain(): String? = fetchText(AUTH_DOMAIN)

/**
 * The sign-in prompt, shown once a call finds the user signed out. It names the accounts that may sign in by
 * [schoolDomain], which starts as [School.DEFAULT_DOMAIN] until the server says otherwise, and after a refused sign-in
 * it says why ([signInError]), in the same terms. [navigate] goes to the sign-in page; a test passes its own.
 */
class SignInDialog(
  signInError: ObservableValue<SignInRefusal?>,
  schoolDomain: ObservableValue<String> = ObservableValue(School.DEFAULT_DOMAIN),
  private val navigate: (String) -> Unit = { window.location.href = it },
) : Dialog<Boolean>(
    caption = DEFAULT_LOGIN_GREETING,
    closeButton = false,
    escape = false,
    animation = true,
  ) {
  init {
    p().bind(schoolDomain) { domain -> +"Sign in with your $domain Google account" }
    p {
      color = Color.name(Col.RED)
      bind(signInError) { refusal ->
        visible = refusal != null
        if (refusal != null) span().bind(schoolDomain) { domain -> +refusal.message(domain) }
      }
    }
    addButton(
      Button("Sign in with Google", "fab fa-google", PRIMARY) {
        onClick { navigate("/$AUTH_LOGIN") }
      },
    )
  }
}
