package com.github.pambrose

import com.github.pambrose.Model.obsTitle
import io.kvision.*
import io.kvision.core.*
import io.kvision.form.text.Text
import io.kvision.html.*
import io.kvision.html.ButtonStyle.OUTLINESECONDARY
import io.kvision.i18n.DefaultI18nManager
import io.kvision.i18n.I18n
import io.kvision.modal.Dialog
import io.kvision.panel.*
import io.kvision.utils.perc
import io.kvision.utils.px
import io.kvision.utils.vh
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
          "pl" to require("i18n/messages-pl.json")
        )
      )

    if (false) {
      root("kvapp") {
        splitPanel {
          width = 100.perc
          height = 100.vh
          add(ListPanel)
          add(EditPanel)
        }
      }

      AppScope.launch {
        Model.getAddressList()
      }
    } else {
      root("kvapp") {
        display = Display.INLINEBLOCK
        add(MainPanel)
      }

      AppScope.launch {
        MainPanel.panel.refreshPanel("/")
      }
    }
  }

  object MainPanel : SimplePanel() {
    val panel = SimplePanel()

    init {
      add(panel)
    }
  }

  suspend fun Container.refreshPanel(title: String) {

    val slideData =
      Security.withAuth {
        Model.lastSlide(title)
      }

    removeAll()

    div {
      margin = 10.px

      div {
        border = Border(2.px, BorderStyle.SOLID, Color.name(Col.WHITE))
        paddingTop = 5.px
        paddingBottom = 5.px
        textAlign = TextAlign.LEFT
        +"Total moves: ${slideData.currentScore}"
      }

      h1 {
        background = Background(Color.rgb(53, 121, 246))
        color = Color.name(Col.WHITE)
        textAlign = TextAlign.CENTER
        +slideData.title
      }

      div {
        border = Border(2.px, BorderStyle.SOLID, Color.name(Col.GRAY))
        padding = 25.px
        add(P(slideData.contents, true))
      }

      div {
        marginTop = 10.px
        val spacing = 4
        val init: Container.() -> Unit = { addButtons(obsTitle.value, slideData.choices, this@refreshPanel) }
        if (slideData.orientation == ChoiceOrientation.VERTICAL)
          vPanel(spacing = spacing, init = init)
        else
          hPanel(spacing = spacing, init = init)
      }

      if (slideData.parentTitles.isNotEmpty()) {
        div {
          marginTop = 10.px

          vPanel {
            button("Go Back In Time", style = ButtonStyle.SUCCESS) {
              onClick {
                val dialog =
                  Dialog<String>("Go back to...") {
                    vPanel(spacing = 4) {
                      slideData.parentTitles.forEach { title ->
                        button(title, style = ButtonStyle.PRIMARY) { onClick { setResult(title) } }
                      }
                    }
                  }

                AppScope.launch {
                  dialog.getResult()?.also { title ->
                    if (title.isNotBlank()) this@refreshPanel.refreshPanel(title)
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  fun Container.addButtons(title: String, choices: List<ChoiceTitle>, mainPanel: Container) {
    choices.forEach { ct ->
      button(ct.choice, style = ButtonStyle.PRIMARY) {
        onClick {
          val submit = Button("OK", disabled = true)
          val dialog =
            Dialog<String>("Reasoning") {
              val input =
                Text(label = "Reason for your decision:") {
                  placeholder = """I chose "${ct.choice}" because..."""
                  setEventListener<Text> {
                    keyup = { e ->
                      submit.disabled = value.isNullOrBlank()
                    }
                  }
                }
              add(input)
              addButton(Button("Cancel", style = OUTLINESECONDARY).also { it.onClick { setResult("") } })
              addButton(submit.also {
                it.onClick {
                  setResult(input.value)
                }
              })
            }

          AppScope.launch {
            dialog.getResult()?.also { response ->
              if (response.isNotBlank()) {
                Model.choose("user1", title, ct.title, ct.choice, response)
                mainPanel.refreshPanel(ct.title)
              }
            }
          }
        }
      }
    }
  }
}

fun main() {
  startApplication(::App, module.hot, BootstrapModule, FontAwesomeModule, CoreModule)
}
