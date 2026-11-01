package com.pambrose.historywalk

import com.pambrose.BuildConfig
import com.pambrose.historywalk.ClientUtils.PAGE_NOT_LOADED
import com.pambrose.historywalk.ClientUtils.launchOrErrorToast
import com.pambrose.historywalk.MainPanel.refresh
import com.pambrose.historywalk.Rpc.getCurrentSlide
import io.kvision.*
import io.kvision.core.Display
import io.kvision.panel.root
import io.kvision.remote.registerRemoteTypes
import io.kvision.utils.useModule
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import org.w3c.dom.PageTransitionEvent
import org.w3c.dom.events.EventTarget
import org.w3c.dom.url.URLSearchParams

// A backstop only: the click handlers and start-up calls report their own failures (ClientUtils.orErrorToast)
private val logUncaught = CoroutineExceptionHandler { _, e -> console.log("Uncaught error: $e") }

// A SupervisorJob, so one failed call cannot cancel the scope and with it every later click
val AppScope = CoroutineScope(window.asCoroutineDispatcher() + SupervisorJob() + logUncaught)

// The version check's own scope, so nothing that goes wrong in the walk can stop it
private val versionCheckScope = CoroutineScope(window.asCoroutineDispatcher() + SupervisorJob() + logUncaught)

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
    reloadWhenRestoredFromCache()
    showRefusalFromUrl()

    root("kvapp") {
      display = Display.INLINEBLOCK
      add(MainPanel)
    }

    launchOrErrorToast(PAGE_NOT_LOADED) {
      val newSlide = getCurrentSlide()
      refresh(newSlide)
    }

    versionCheckScope.launch { ClientVersionCheck.checkClientVersion() }
  }
}

/**
 * Reloads the page when the browser restores it from its back/forward cache (a `pageshow` with `persisted` set), as
 * Back can after signing out: the restored page would show the previous user's walk without asking the server, while
 * the reload asks it, so a signed-out browser gets the sign-in dialog. An ordinary load's `pageshow` is not persisted.
 * [target] and [reload] are the page's own unless a test passes others.
 */
internal fun reloadWhenRestoredFromCache(
  target: EventTarget = window,
  reload: () -> Unit = { window.location.reload() },
) {
  target.addEventListener("pageshow", { event ->
    if (event.unsafeCast<PageTransitionEvent>().persisted) reload()
  })
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
