package com.github.pambrose

import com.github.pambrose.ClientUtils.lowercase
import io.kvision.core.onEvent
import io.kvision.form.FormPanel
import io.kvision.form.formPanel
import io.kvision.form.text.Password
import io.kvision.form.text.Text
import io.kvision.html.Button
import io.kvision.html.ButtonStyle
import io.kvision.html.ButtonStyle.PRIMARY
import io.kvision.i18n.tr
import io.kvision.modal.Alert
import io.kvision.modal.Dialog
import io.kvision.remote.Credentials
import io.kvision.utils.ENTER_KEY
import kotlinx.coroutines.launch

class LoginWindow : Dialog<Credentials>(closeButton = false, escape = false, animation = true) {

  private val loginPanel: FormPanel<Credentials>
  private val loginButton: Button
  private val userButton: Button
  private val registerPanel: FormPanel<RegisterData>
  private val registerButton: Button
  private val cancelButton: Button

  init {
    loginPanel =
      formPanel {
        add(Credentials::username, Text(label = "${tr("Email")}:"), required = true).apply { focus() }
        add(Credentials::password, Password(label = "${tr("Password")}:"), required = true)
        onEvent {
          keydown = {
            if (it.keyCode == ENTER_KEY) {
              this@LoginWindow.processCredentials()
            }
          }
        }
      }

    registerPanel =
      formPanel {
        add(RegisterData::fullName, Text(label = "${tr("Full name")}:"), required = true)
        add(RegisterData::email, Text(label = "Email:"), required = true)
        add(
          RegisterData::password, Password(label = "${tr("Password")}:"), required = true,
          validatorMessage = { "Password too short" }) {
          (it.getValue()?.length ?: 0) >= 8
        }
        add(
          RegisterData::password2, Password(label = "${tr("Confirm password")}:"), required = true,
          validatorMessage = { tr("Password too short") }) {
          (it.getValue()?.length ?: 0) >= 8
        }
        validator = {
          val result = it[RegisterData::password] == it[RegisterData::password2]
          if (!result) {
            it.getControl(RegisterData::password)?.validatorError = tr("Passwords are not the same")
            it.getControl(RegisterData::password2)?.validatorError = tr("Passwords are not the same")
          }
          result
        }
        validatorMessage = { tr("Passwords are not the same") }

      }

    cancelButton =
      Button(tr("Cancel"), "fas fa-times", ButtonStyle.SECONDARY).apply {
        lowercase()
        onClick {
          this@LoginWindow.hideRegisterForm()
        }
      }

    registerButton = Button(tr("Register"), "fas fa-check", PRIMARY).apply {
      lowercase()
      onClick {
        this@LoginWindow.processRegister()
      }
    }

    loginButton = Button(tr("Login"), "fas fa-check", PRIMARY).apply {
      lowercase()
      onClick {
        this@LoginWindow.processCredentials()
      }
    }

    userButton = Button(tr("Sign Up"), "fas fa-user").apply {
      lowercase()
      onClick {
        this@LoginWindow.showRegisterForm()
      }
    }

    addButton(userButton)
    addButton(loginButton)
    addButton(cancelButton)
    addButton(registerButton)
    hideRegisterForm()
  }

  private fun showRegisterForm() {
    loginPanel.hide()
    registerPanel.show().focus()
    registerPanel.clearData()
    loginButton.hide()
    userButton.hide()
    cancelButton.show()
    registerButton.show()
  }

  private fun hideRegisterForm() {
    loginPanel.show().focus()
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
        if (Rpc.registerUser(registerData))
          Alert.show(text = tr("User registered. You can now log in.")) {
            hideRegisterForm()
          }
        else
          Alert.show(text = tr("Unsuccessful login. Please try again."))
      }
    }
  }
}