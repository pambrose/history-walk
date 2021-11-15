package com.github.pambrose

import com.github.pambrose.ClientUtils.lowercase
import com.github.pambrose.ClientUtils.verticalPadding
import com.github.pambrose.EndPoints.LOGOUT
import com.github.pambrose.MainPanel.buttonPadding
import com.github.pambrose.MainPanel.refresh
import io.kvision.core.*
import io.kvision.form.text.Text
import io.kvision.form.text.TextArea
import io.kvision.html.*
import io.kvision.html.ButtonStyle.*
import io.kvision.modal.Dialog
import io.kvision.panel.*
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
    margin = 20.px
    width = 600.px

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
          lowercase()
          onClick {
            document.location?.href = "/$LOGOUT"
          }
        }
      }
    }

    vPanel {
      slide.parentTitles.forEach { parentTitle ->
//        span {
//          background = Background(Color.rgb(53, 121, 246))
//          color = Color.name(Col.WHITE)
//          textAlign = TextAlign.CENTER
//          +parent
//        }
        button(parentTitle, style = SUCCESS) {
          onClick {
            AppScope.launch {
              val newSlide = Rpc.goBackInTime(parentTitle)
              refresh(newSlide)
            }
          }
        }
        hPanel(justify = JustifyContent.CENTER) {
          paddingTop = 5.px
          paddingBottom = 5.px
          icon("fas fa-arrow-alt-circle-down")
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

    if (false && slide.parentTitles.isNotEmpty()) {
      simplePanel {
        marginTop = 10.px

        vPanel {
          button("Go Back In Time", icon = "fas fa-arrow-alt-circle-up", style = SUCCESS) {
            lowercase()
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
                          lowercase()
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
      lowercase()
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

private fun String?.wordCount(): Int = this?.split(" ")?.filter { it.isNotEmpty() }?.size ?: 0

private fun promptForReason(fromTitle: String, ct: ChoiceTitle) {
  val submit = Button("OK", disabled = true).apply {
    lowercase()
    verticalPadding(buttonPadding)
  }
  val wordCount = Span(content = "0 words") {
    float = PosFloat.RIGHT
  }
  val reasonDialog =
    Dialog<String>("Reasoning") {
      val input =
        TextArea(rows = 3, label = """Reason for your "${ct.abbrev}" decision:""") {
//          marginLeft = 25.px
//          paddingLeft = 25.px
          placeholder = """ I chose "${ct.abbrev}" because..."""
          autofocus = true
          setEventListener<Text> {
            keyup = { _ ->
              // Make sure the user has typed something.
              value.wordCount().also { wc ->
                submit.disabled = wc < 5
                wordCount.content = "$wc words"
              }
            }
          }
        }
      add(input)
      add(wordCount)
      addButton(Button("Cancel", style = OUTLINESECONDARY).apply {
        lowercase()
        verticalPadding(buttonPadding)
        onClick { setResult("") }
      })
      addButton(submit.apply { onClick { setResult(input.value) } })
//      onEvent {
//        keydown = {
//          if (it.keyCode == ENTER_KEY && input.value?.isNotEmpty() ?: false) {
//            setResult(input.value)
//          }
//        }
//      }
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