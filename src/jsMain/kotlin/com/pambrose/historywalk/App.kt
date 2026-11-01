package com.pambrose.historywalk

import com.pambrose.BuildConfig
import com.pambrose.historywalk.MainPanel.refresh
import com.pambrose.historywalk.Rpc.getCurrentSlide
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

@JsModule("/kotlin/modules/css/kvapp.css")
external object KvappCss

class App : Application() {
  init {
    useModule(KvappCss)
  }

  override fun start() {
    // Also the bundle's version marker: make served-version finds this string in main.bundle.js to tell which
    // release Heroku and Cloudflare each serve
    console.log("History Walk version ${BuildConfig.VERSION} - ${BuildConfig.RELEASE_DATE}")
    showRefusalFromUrl()

    root("kvapp") {
      display = Display.INLINEBLOCK
      add(MainPanel)
    }

    AppScope.launch {
      val newSlide = getCurrentSlide()
      refresh(newSlide)
    }

    AppScope.launch { ClientVersionCheck.checkClientVersion() }
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
  startApplication(
    ::App,
    js("import.meta.webpackHot").unsafeCast<Hot?>(),
    BootstrapModule,
    FontAwesomeModule,
    CoreModule,
  )
}
