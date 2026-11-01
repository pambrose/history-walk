package com.github.pambrose

import com.github.pambrose.MainPanel.refresh
import com.github.pambrose.Rpc.getCurrentSlide
import io.kvision.*
import io.kvision.core.Display
import io.kvision.panel.root
import io.kvision.remote.registerRemoteTypes
import io.kvision.utils.useModule
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import org.w3c.dom.url.URLSearchParams

val AppScope = CoroutineScope(window.asCoroutineDispatcher())

@JsModule("./modules/css/kvapp.css")
external object kvappCss

class App : Application() {
  init {
    useModule(kvappCss)
  }

  override fun start() {
    showRefusalFromUrl()

    root("kvapp") {
      display = Display.INLINEBLOCK
      add(MainPanel)
    }

    AppScope.launch {
      val newSlide = getCurrentSlide()
      refresh(newSlide)
    }
  }
}

private fun showRefusalFromUrl() {
  val params = URLSearchParams(window.location.search)
  val code = params.get(SignInError.ERROR_PARAM) ?: return
  val email = SignInError.refusalEmailOrNull(params.get(SignInError.EMAIL_PARAM))
  Security.signInError.value = SignInError.fromCode(code)?.message(email)
  window.history.replaceState(null, "", "/")
}

fun main() {
  registerRemoteTypes()
  startApplication(::App, js("import.meta.webpackHot").unsafeCast<Hot?>(), BootstrapModule, FontAwesomeModule, CoreModule)
}
