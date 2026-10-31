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

val AppScope = CoroutineScope(window.asCoroutineDispatcher())

@JsModule("./modules/css/kvapp.css")
external object kvappCss

class App : Application() {
  init {
    useModule(kvappCss)
  }

  override fun start() {
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

fun main() {
  registerRemoteTypes()
  startApplication(::App, js("import.meta.webpackHot").unsafeCast<Hot?>(), BootstrapModule, FontAwesomeModule, CoreModule)
}
