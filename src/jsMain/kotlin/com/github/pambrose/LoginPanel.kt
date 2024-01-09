package com.github.pambrose

import com.github.pambrose.ClientUtils.lowercase
import com.github.pambrose.Rpc.registerUser
import io.kvision.core.onEvent
import io.kvision.form.FormPanel
import io.kvision.form.formPanel
import io.kvision.form.text.Password
import io.kvision.form.text.Text
import io.kvision.html.Button
import io.kvision.html.ButtonStyle
import io.kvision.html.ButtonStyle.PRIMARY
import io.kvision.modal.Alert
import io.kvision.modal.Dialog
import io.kvision.remote.Credentials
import io.kvision.utils.ENTER_KEY
import kotlinx.coroutines.launch

class LoginPanel : Dialog<Credentials>(closeButton = false, escape = false, animation = true) {
  private val loginPanel: FormPanel<Credentials>
  private val loginButton: Button
  private val userButton: Button
  private val registerPanel: FormPanel<RegisterData>
  private val registerButton: Button
  private val cancelButton: Button

  init {
    loginPanel =
      formPanel {
        add(
          Credentials::username,
          Text(label = "${"Email"}:").apply {
            autofocus = true
            focus()
          },
          required = true,
        )

        add(
          Credentials::password,
          Password(label = "${"Password"}:"),
          required = true,
        )
        onEvent {
          keydown = {
            if (it.keyCode == ENTER_KEY) {
              this@LoginPanel.processCredentials()
            }
          }
        }
      }

    registerPanel =
      formPanel {
        add(RegisterData::fullName, Text(label = "${"Full name"}:"), required = true)
        add(RegisterData::email, Text(label = "Email:"), required = true)
        add(
          RegisterData::password, Password(label = "${"Password"}:"), required = true,
          validatorMessage = { "Password too short" },
        ) {
          (it.getValue()?.length ?: 0) >= 8
        }
        add(
          RegisterData::password2, Password(label = "${"Confirm password"}:"), required = true,
          validatorMessage = { "Password too short" },
        ) {
          (it.getValue()?.length ?: 0) >= 8
        }
        validator = {
          val pwsAreEqual = it[RegisterData::password] == it[RegisterData::password2]
          if (!pwsAreEqual) {
            it.getControl(RegisterData::password)?.validatorError = "Passwords are not the same"
            it.getControl(RegisterData::password2)?.validatorError = "Passwords are not the same"
          }
          pwsAreEqual
        }
        validatorMessage = { "Passwords are not the same" }
      }

    cancelButton =
      Button("Cancel", "fas fa-times", ButtonStyle.SECONDARY).apply {
        lowercase()
        onClick {
          this@LoginPanel.showLoginForm()
        }
      }

    registerButton = Button("Register", "fas fa-check", PRIMARY).apply {
      lowercase()
      onClick {
        this@LoginPanel.processRegister()
      }
    }

    loginButton = Button("Login", "fas fa-check", PRIMARY).apply {
      lowercase()
      onClick {
        this@LoginPanel.processCredentials()
      }
    }

    userButton = Button("Sign Up", "fas fa-user").apply {
      lowercase()
      onClick {
        this@LoginPanel.showRegisterForm()
      }
    }

    addButton(userButton)
    addButton(loginButton)
    addButton(cancelButton)
    addButton(registerButton)
    showLoginForm()
  }

  private fun showRegisterForm() {
    loginPanel.hide()
    registerPanel.apply {
      show()
      focus()
      clearData()
    }
    loginButton.hide()
    userButton.hide()
    cancelButton.show()
    registerButton.show()
  }

  private fun showLoginForm() {
    loginPanel.apply {
      show()
      focus()
    }
    registerPanel.hide()
    loginButton.show()
    userButton.show()
    cancelButton.hide()
    registerButton.hide()
  }

  private fun processCredentials() {
    if (loginPanel.validate()) {
      setResult(loginPanel.getData())
      loginPanel.clearData()
    }
  }

  private fun processRegister() {
    if (registerPanel.validate()) {
      val registerData = registerPanel.getData()
      AppScope.launch {
        if (registerUser(registerData))
          Alert.show(text = "User registered. You can now log in.") {
            showLoginForm()
          }
        else
          Alert.show(text = "Unsuccessful registration. Please try again.")
      }
    }
  }
}
