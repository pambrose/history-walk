package com.github.pambrose

import com.github.pambrose.MainPanel.refresh
import io.kvision.*
import io.kvision.core.Display
import io.kvision.panel.root
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch

val AppScope = CoroutineScope(window.asCoroutineDispatcher())

class App : Application() {
  init {
    require("css/kvapp.css")
  }

  override fun start() {
    root("kvapp") {
      display = Display.INLINEBLOCK
      add(MainPanel)
    }

    AppScope.launch {
      val newSlide = Rpc.getCurrentSlide()
      refresh(newSlide)
    }
  }
}

fun main() {
  startApplication(::App, module.hot, BootstrapModule, FontAwesomeModule, CoreModule)
}
