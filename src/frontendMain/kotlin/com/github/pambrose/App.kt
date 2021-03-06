package com.github.pambrose

import com.github.pambrose.MainPanel.refresh
import io.kvision.Application
import io.kvision.BootstrapModule
import io.kvision.CoreModule
import io.kvision.FontAwesomeModule
import io.kvision.core.Display
import io.kvision.i18n.DefaultI18nManager
import io.kvision.i18n.I18n
import io.kvision.module
import io.kvision.panel.root
import io.kvision.require
import io.kvision.startApplication
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
    I18n.manager =
      DefaultI18nManager(
        mapOf(
          "en" to require("i18n/messages-en.json"),
//          "pl" to require("i18n/messages-pl.json")
        )
      )

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
