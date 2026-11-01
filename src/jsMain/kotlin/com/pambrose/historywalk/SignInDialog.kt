package com.pambrose.historywalk

import com.pambrose.historywalk.Auth.DEFAULT_LOGIN_GREETING
import com.pambrose.historywalk.EndPoints.AUTH_GREETING
import com.pambrose.historywalk.EndPoints.AUTH_LOGIN
import io.kvision.core.Col
import io.kvision.core.Color
import io.kvision.html.Button
import io.kvision.html.ButtonStyle.PRIMARY
import io.kvision.html.p
import io.kvision.modal.Dialog
import io.kvision.state.ObservableValue
import io.kvision.state.bind
import kotlinx.browser.window
import kotlinx.coroutines.await

/**
 * The sign-in dialog's title from the server (LOGIN_GREETING, served at [AUTH_GREETING]), or null when it cannot be
 * fetched, in which case the dialog keeps [DEFAULT_LOGIN_GREETING].
 */
suspend fun fetchLoginGreeting(): String? =
  try {
    val response = window.fetch("/$AUTH_GREETING").await()
    if (response.ok) response.text().await().trim().takeIf { it.isNotEmpty() } else null
  } catch (e: Throwable) {
    console.log("Could not fetch the sign-in greeting: $e")
    null
  }

/**
 * The sign-in prompt, shown once a call finds the user signed out. After a refused sign-in it says why
 * ([signInError]). [navigate] goes to the sign-in page; a test passes its own.
 */
class SignInDialog(
  signInError: ObservableValue<String?>,
  private val navigate: (String) -> Unit = { window.location.href = it },
) : Dialog<Boolean>(
    caption = DEFAULT_LOGIN_GREETING,
    closeButton = false,
    escape = false,
    animation = true,
  ) {
  init {
    p("Sign in with your Athenian Google account")
    p {
      color = Color.name(Col.RED)
      bind(signInError) { error ->
        visible = error != null
        +error.orEmpty()
      }
    }
    addButton(
      Button("Sign in with Google", "fab fa-google", PRIMARY) {
        onClick { navigate("/$AUTH_LOGIN") }
      },
    )
  }
}
