package com.github.pambrose

import com.github.pambrose.EndPoints.LOGOUT
import com.github.pambrose.MainPanel.buttonPadding
import com.github.pambrose.MainPanel.refresh
import io.kvision.core.AlignItems
import io.kvision.core.Background
import io.kvision.core.Border
import io.kvision.core.BorderStyle
import io.kvision.core.Col
import io.kvision.core.Color
import io.kvision.core.Container
import io.kvision.core.FlexDirection
import io.kvision.core.FlexWrap
import io.kvision.core.JustifyContent
import io.kvision.core.TextAlign
import io.kvision.core.TextTransform
import io.kvision.core.onEvent
import io.kvision.form.text.Text
import io.kvision.html.Button
import io.kvision.html.ButtonStyle
import io.kvision.html.ButtonStyle.OUTLINESECONDARY
import io.kvision.html.ButtonStyle.PRIMARY
import io.kvision.html.ButtonStyle.SUCCESS
import io.kvision.html.P
import io.kvision.html.button
import io.kvision.html.h1
import io.kvision.modal.Dialog
import io.kvision.panel.SimplePanel
import io.kvision.panel.flexPanel
import io.kvision.panel.hPanel
import io.kvision.panel.simplePanel
import io.kvision.panel.vPanel
import io.kvision.utils.ENTER_KEY
import io.kvision.utils.px
import kotlinx.browser.document
import kotlinx.coroutines.launch

object MainPanel : SimplePanel() {
  val buttonPadding = 5.px

  val panel = SimplePanel()

  init {
    add(panel)
  }

  fun refresh(slide: SlideData) = panel.displaySlide(slide)
}

private fun Container.displaySlide(slide: SlideData) {

  removeAll()

  simplePanel {
    margin = 10.px

//    simplePanel {
//      border = Border(2.px, BorderStyle.SOLID, Color.name(Col.WHITE))
//      paddingTop = 5.px
//      paddingBottom = 5.px
//      textAlign = TextAlign.LEFT
//      +"Decision count: ${slide.decisionCount}"
//    }

    simplePanel {
      flexPanel(FlexDirection.ROW, FlexWrap.WRAP, JustifyContent.SPACEBETWEEN, AlignItems.CENTER, spacing = 5) {
        paddingBottom = 10.px

        +"Decision count: ${slide.decisionCount}"
        button("Logout", "fas fa-sign-out-alt", style = ButtonStyle.LINK) {
          textTransform = TextTransform.NONE
          onClick {
            document.location?.href = "/$LOGOUT"
          }
        }
      }
    }

    h1 {
      background = Background(Color.rgb(53, 121, 246))
      color = Color.name(Col.WHITE)
      textAlign = TextAlign.CENTER
      +slide.title
    }

    simplePanel {
      border = Border(2.px, BorderStyle.SOLID, Color.name(Col.GRAY))
      padding = 25.px
      add(P(slide.contents, true))
    }

    simplePanel {
      marginTop = 10.px
      val spacing = 4
      val init: Container.() -> Unit = { addChoiceButtons(slide) }
      if (slide.orientation == ChoiceOrientation.VERTICAL)
        vPanel(spacing = spacing, init = init)
      else
        hPanel(spacing = spacing, init = init)
    }

    if (slide.parentTitles.isNotEmpty()) {
      simplePanel {
        marginTop = 10.px

        vPanel {
          button("Go Back In Time", icon = "fas fa-arrow-alt-circle-up", style = SUCCESS) {
            textTransform = TextTransform.NONE
            onClick {
              if (slide.parentTitles.size == 1) {
                slide.parentTitles[0].also { parentTitle ->
                  if (parentTitle.isNotBlank())
                    AppScope.launch {
                      val newSlide = Rpc.goBackInTime(parentTitle)
                      refresh(newSlide)
                    }
                }
              } else {
                val dialog =
                  Dialog<String>("Go back to...") {
                    vPanel(spacing = 4) {
                      slide.parentTitles.forEach { parentTitle ->
                        button(parentTitle, style = PRIMARY) {
                          textTransform = TextTransform.NONE
                          onClick { setResult(parentTitle) }
                        }
                      }
                    }
                  }

                AppScope.launch {
                  dialog.getResult()?.also { parentTitle ->
                    if (parentTitle.isNotBlank()) {
                      val newSlide = Rpc.goBackInTime(parentTitle)
                      refresh(newSlide)
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

private fun Container.addChoiceButtons(currentSlide: SlideData) {
  currentSlide.choices.forEach { ct ->
    button(ct.abbrev, icon = "fas fa-angle-double-right", style = PRIMARY) {
      textTransform = TextTransform.NONE
      onClick {
        AppScope.launch {
          val choiceReason = Rpc.makeChoice(currentSlide.title, ct.abbrev, ct.title)
          if (choiceReason.reason.isEmpty())
            promptForReason(currentSlide.title, ct)
          else {
            val newSlide = Rpc.refreshPanel()
            refresh(newSlide)
          }
        }
      }
    }
  }
}

private fun promptForReason(fromTitle: String, ct: ChoiceTitle) {
  val submit = Button("OK", disabled = true).apply {
    textTransform = TextTransform.NONE
    paddingTop = buttonPadding
    paddingBottom = buttonPadding
  }
  val reasonDialog =
    Dialog<String>("Reasoning") {
      val input =
        Text(label = """Reason for your "${ct.abbrev}" decision:""") {
          placeholder = """I chose "${ct.abbrev}" because..."""
          setEventListener<Text> {
            keyup = { _ ->
              submit.disabled = value.isNullOrBlank()
            }
          }
        }
      add(input)
      addButton(Button("Cancel", style = OUTLINESECONDARY).apply {
        textTransform = TextTransform.NONE
        paddingTop = buttonPadding
        paddingBottom = buttonPadding
        onClick { setResult("") }
      })
      addButton(submit.also { it.onClick { setResult(input.value) } })
      onEvent {
        keydown = {
          if (it.keyCode == ENTER_KEY && input.value?.isNotEmpty() ?: false) {
            setResult(input.value)
          }
        }
      }
    }

  AppScope.launch {
    reasonDialog.getResult()?.also { response ->
      if (response.isNotBlank()) {
        val newSlide = Rpc.provideReason(fromTitle, ct.abbrev, ct.title, response)
        refresh(newSlide)
      }
    }
  }
}