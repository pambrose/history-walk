package com.github.pambrose

import com.github.pambrose.EndPoints.LOGOUT
import com.github.pambrose.MainPanel.displaySlide
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
import io.kvision.form.text.Text
import io.kvision.html.Button
import io.kvision.html.ButtonStyle
import io.kvision.html.P
import io.kvision.html.button
import io.kvision.html.h1
import io.kvision.i18n.tr
import io.kvision.modal.Dialog
import io.kvision.panel.SimplePanel
import io.kvision.panel.flexPanel
import io.kvision.panel.hPanel
import io.kvision.panel.simplePanel
import io.kvision.panel.vPanel
import io.kvision.utils.px
import kotlinx.browser.document
import kotlinx.coroutines.launch

object MainPanel : SimplePanel() {
  val panel = SimplePanel()

  init {
    add(panel)
  }

  fun displaySlide(slide: SlideData) = panel.displaySlide(slide)
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
        button(tr("Logout"), "fas fa-sign-out-alt", style = ButtonStyle.LINK).onClick {
          document.location?.href = "/$LOGOUT"
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
          button(tr("Go Back In Time"), style = ButtonStyle.SUCCESS) {
            onClick {
              if (slide.parentTitles.size == 1) {
                slide.parentTitles[0].also { parentTitle ->
                  if (parentTitle.isNotBlank())
                    AppScope.launch {
                      val newSlide = Rpc.goBackInTime(parentTitle)
                      displaySlide(newSlide)
                    }
                }
              } else {
                val dialog =
                  Dialog<String>("Go back to...") {
                    vPanel(spacing = 4) {
                      slide.parentTitles.forEach { parentTitle ->
                        button(tr(parentTitle), style = ButtonStyle.PRIMARY) { onClick { setResult(parentTitle) } }
                      }
                    }
                  }

                AppScope.launch {
                  dialog.getResult()?.also { parentTitle ->
                    if (parentTitle.isNotBlank()) {
                      val newSlide = Rpc.goBackInTime(parentTitle)
                      displaySlide(newSlide)
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
    button(tr(ct.abbrev), style = ButtonStyle.PRIMARY) {
      onClick {
        AppScope.launch {
          val choiceReason = Rpc.makeChoice(currentSlide.title, ct.abbrev, ct.title)
          if (choiceReason.reason.isEmpty())
            promptForReason(currentSlide.title, ct)
          else {
            val newSlide = Rpc.refreshPanel()
            displaySlide(newSlide)
          }
        }
      }
    }
  }
}

private fun promptForReason(fromTitle: String, ct: ChoiceTitle) {
  val submit = Button("OK", disabled = true)
  val reasonDialog =
    Dialog<String>("Reasoning") {
      val input =
        Text(label = "Reason for your decision:") {
          placeholder = """I chose "${ct.abbrev}" because..."""
          setEventListener<Text> {
            keyup = { _ ->
              submit.disabled = value.isNullOrBlank()
            }
          }
        }
      add(input)
      addButton(Button("Cancel", style = ButtonStyle.OUTLINESECONDARY).also { it.onClick { setResult("") } })
      addButton(submit.also { it.onClick { setResult(input.value) } })
    }

  AppScope.launch {
    reasonDialog.getResult()?.also { response ->
      if (response.isNotBlank()) {
        val newSlide = Rpc.provideReason(fromTitle, ct.abbrev, ct.title, response)
        displaySlide(newSlide)
      }
    }
  }
}