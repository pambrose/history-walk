package com.github.pambrose

import io.kvision.core.*
import io.kvision.form.text.Text
import io.kvision.html.*
import io.kvision.modal.Dialog
import io.kvision.panel.SimplePanel
import io.kvision.panel.hPanel
import io.kvision.panel.vPanel
import io.kvision.utils.px
import kotlinx.coroutines.launch

object MainPanel : SimplePanel() {
  val panel = SimplePanel()

  init {
    add(panel)
  }

}

fun Container.displaySlide(currentSlide: SlideData) {

  removeAll()

  div {
    margin = 10.px

    div {
      border = Border(2.px, BorderStyle.SOLID, Color.name(Col.WHITE))
      paddingTop = 5.px
      paddingBottom = 5.px
      textAlign = TextAlign.LEFT
      +"Total moves: ${currentSlide.currentScore}"
    }

    h1 {
      background = Background(Color.rgb(53, 121, 246))
      color = Color.name(Col.WHITE)
      textAlign = TextAlign.CENTER
      +currentSlide.title
    }

    div {
      border = Border(2.px, BorderStyle.SOLID, Color.name(Col.GRAY))
      padding = 25.px
      add(P(currentSlide.contents, true))
    }

    div {
      marginTop = 10.px
      val spacing = 4
      val init: Container.() -> Unit = { addButtons(currentSlide) }
      if (currentSlide.orientation == ChoiceOrientation.VERTICAL)
        vPanel(spacing = spacing, init = init)
      else
        hPanel(spacing = spacing, init = init)
    }

    if (currentSlide.parentTitles.isNotEmpty()) {
      div {
        marginTop = 10.px

        vPanel {
          button("Go Back In Time", style = ButtonStyle.SUCCESS) {
            onClick {
              val dialog =
                Dialog<String>("Go back to...") {
                  vPanel(spacing = 4) {
                    currentSlide.parentTitles.forEach { slideTitle ->
                      button(slideTitle, style = ButtonStyle.PRIMARY) { onClick { setResult(slideTitle) } }
                    }
                  }
                }

              AppScope.launch {
                dialog.getResult()?.also { slideTitle ->
                  if (slideTitle.isNotBlank())
                    Model.refreshPanel(slideTitle)
                }
              }
            }
          }
        }
      }
    }
  }
}

private fun Container.addButtons(currentSlide: SlideData) {
  currentSlide.choices.forEach { ct ->
    button(ct.choice, style = ButtonStyle.PRIMARY) {
      onClick {
        AppScope.launch {
          val choiceReason = Model.choose(currentSlide.title, ct.title, ct.choice)
          if (choiceReason.reason.isEmpty())
            promptForReason(ct, choiceReason.choiceId)
          else
            Model.refreshPanel(ct.title)
        }
      }
    }
  }
}

private fun promptForReason(ct: ChoiceTitle, choiceId: String) {
  val submit = Button("OK", disabled = true)
  val reasonDialog =
    Dialog<String>("Reasoning") {
      val input =
        Text(label = "Reason for your decision:") {
          placeholder = """I chose "${ct.choice}" because..."""
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
        Model.reason(choiceId, response)
        Model.refreshPanel(ct.title)
      }
    }
  }
}