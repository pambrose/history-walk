package com.github.pambrose

import com.github.pambrose.ClientUtils.lowercase
import com.github.pambrose.ClientUtils.verticalPadding
import com.github.pambrose.EndPoints.CONTENT_RESET
import com.github.pambrose.EndPoints.LOGOUT
import com.github.pambrose.MainPanel.buttonPadding
import com.github.pambrose.MainPanel.refresh
import com.github.pambrose.MainPanel.userInfo
import io.kvision.core.*
import io.kvision.form.text.Text
import io.kvision.form.text.TextArea
import io.kvision.html.*
import io.kvision.html.ButtonStyle.*
import io.kvision.modal.Dialog
import io.kvision.panel.*
import io.kvision.state.ObservableValue
import io.kvision.state.bind
import io.kvision.utils.px
import kotlinx.browser.document
import kotlinx.coroutines.launch
import kotlin.math.abs

object MainPanel : SimplePanel() {
  val buttonPadding = 5.px
  val panel = SimplePanel()
  val userInfo = ObservableValue(UserInfo("", ""))

  init {
    add(panel)

    AppScope.launch {
      userInfo.value = Rpc.getUserInfo()
    }
  }

  fun refresh(slide: SlideData) = panel.displaySlide(slide)
}

private fun Container.displaySlide(slide: SlideData) {

  removeAll()

  simplePanel {
    margin = 20.px
    width = 700.px

    simplePanel {
      flexPanel(FlexDirection.ROW, FlexWrap.WRAP, JustifyContent.SPACEBETWEEN, AlignItems.CENTER, spacing = 5) {
        paddingBottom = 10.px

        vPanel {
          hPanel().bind(userInfo) {
            +it.email
          }
          hPanel { +"Decision count: ${slide.decisionCount}" }
        }
        button("Logout", "fas fa-sign-out-alt", style = LINK) {
          lowercase()
          onClick {
            document.location?.href = "/$LOGOUT"
          }
        }

        if (slide.showResetButton)
          button("Reset", "fas fa-sync-alt", style = LINK) {
            lowercase()
            onClick {
              document.location?.href = "/$CONTENT_RESET"
            }
          }
      }
    }

    vPanel {
      slide.parentTitles
        .forEach { parentTitle ->
          button(parentTitle.title, style = SUCCESS) {
            paddingTop = 5.px
            paddingBottom = 5.px
            onClick {
              AppScope.launch {
                Rpc.goBackInTime(parentTitle).also { refresh(it) }
              }
            }
          }
          hPanel(justify = JustifyContent.CENTER) {
            paddingTop = 4.px
            paddingBottom = 4.px
            icon("fas fa-arrow-alt-circle-down")
          }
        }
    }

    if (slide.displayTitle)
      h1 {
        background = Background(Color.rgb(53, 121, 246))
        color = Color.name(Col.WHITE)
        textAlign = TextAlign.CENTER
        +slide.title
      }

    simplePanel {
      if (slide.failure)
        background = Background(Color.rgb(255, 99, 71))

      if (slide.success)
        background = Background(Color.rgb(89, 181, 95))

      border = Border(2.px, BorderStyle.SOLID, Color.name(Col.GRAY))
      paddingTop = 25.px
      paddingLeft = 25.px
      paddingRight = 25.px
      paddingBottom = 5.px

      add(P(slide.content, true))
    }

    if (slide.success) {
      flexPanel(FlexDirection.ROW, FlexWrap.WRAP, JustifyContent.FLEXEND, AlignItems.CENTER, spacing = 5) {
        button("Go Home", "fas fa-arrow-alt-circle-up", style = LINK) {
          lowercase()
          onClick {
            AppScope.launch {
              Rpc.goBackInTime(ParentTitle("/", "/")).also { refresh(it) }
            }
          }
        }
      }
    }
    else {
      simplePanel {
        marginTop = 10.px
        val spacing = 4
        val init: Container.() -> Unit = { addChoiceButtons(slide) }
        if (slide.verticalChoices)
          vPanel(spacing = spacing, init = init)
        else
          hPanel(spacing = spacing, init = init)
      }
    }
  }

}

private fun Container.addChoiceButtons(slide: SlideData) {
  slide.choices
    .forEach { choice ->
      button(choice.choiceText, icon = "fas fa-angle-double-right", style = PRIMARY) {
        lowercase()
        onClick {
          AppScope.launch {
            if (choice.offset != 0) {
              val pos = slide.parentTitles.size - abs(slide.offset) - 1
              val parentTitle = slide.parentTitles[pos]
              Rpc.goBackInTime(parentTitle).also { refresh(it) }
            }
            else {
              val choiceReason = Rpc.makeChoice(slide.pathName, slide.title, choice, slide.choices.size == 1)
              if (choiceReason.reason.isEmpty()) {
                promptUserForReason(slide.pathName, slide.title, choice)
              }
              else {
                val newSlide = Rpc.getCurrentSlide()
                refresh(newSlide)
              }
            }
          }
        }
      }
    }
}

private fun String?.wordCount(): Int = this?.split(" ", "\n")?.filter { it.isNotEmpty() }?.size ?: 0

private fun promptUserForReason(fromPathName: String, fromTitle: String, slideChoice: SlideChoice) {
  val submit = Button("OK", disabled = true).apply {
    lowercase()
    verticalPadding(buttonPadding)
  }
  val wordCount = Span(content = "0 words") { float = PosFloat.RIGHT }
  val reasonDialog =
    Dialog<String>("Reasoning") {
      val input =
        TextArea(rows = 3, label = """Reason for your "${slideChoice.choiceText}" decision:""") {
          placeholder = """ I chose "${slideChoice.choiceText}" because..."""
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
        val newSlide = Rpc.provideReason(fromPathName, fromTitle, slideChoice, response)
        refresh(newSlide)
      }
    }
  }
}