package com.pambrose.historywalk

import com.pambrose.historywalk.ClientUtils.CLICK_FAILED
import com.pambrose.historywalk.ClientUtils.PAGE_NOT_LOADED
import com.pambrose.historywalk.ClientUtils.REASON_NOT_SAVED
import com.pambrose.historywalk.ClientUtils.launchOrErrorToast
import com.pambrose.historywalk.ClientUtils.lowercase
import com.pambrose.historywalk.ClientUtils.orErrorToast
import com.pambrose.historywalk.ClientUtils.postTo
import com.pambrose.historywalk.ClientUtils.verticalPadding
import com.pambrose.historywalk.EndPoints.LOGOUT
import com.pambrose.historywalk.EndPoints.REASONS
import com.pambrose.historywalk.EndPoints.SUMMARY
import com.pambrose.historywalk.EndPoints.USER_RESET
import com.pambrose.historywalk.MainPanel.buttonPadding
import com.pambrose.historywalk.MainPanel.refresh
import com.pambrose.historywalk.MainPanel.userInfo
import com.pambrose.historywalk.Rpc.getCurrentSlide
import com.pambrose.historywalk.Rpc.getUserInfo
import com.pambrose.historywalk.Rpc.goBackInTime
import com.pambrose.historywalk.Rpc.makeChoice
import com.pambrose.historywalk.Rpc.provideReason
import io.kvision.core.AlignItems
import io.kvision.core.Background
import io.kvision.core.Border
import io.kvision.core.BorderStyle
import io.kvision.core.Col
import io.kvision.core.Color
import io.kvision.core.Container
import io.kvision.core.Display
import io.kvision.core.FlexDirection
import io.kvision.core.FlexWrap
import io.kvision.core.FontStyle
import io.kvision.core.FontWeight
import io.kvision.core.JustifyContent
import io.kvision.core.PosFloat
import io.kvision.core.TextAlign
import io.kvision.form.text.TextArea
import io.kvision.html.Align
import io.kvision.html.Button
import io.kvision.html.ButtonStyle.*
import io.kvision.html.P
import io.kvision.html.Span
import io.kvision.html.button
import io.kvision.html.div
import io.kvision.html.h1
import io.kvision.html.icon
import io.kvision.html.image
import io.kvision.html.span
import io.kvision.modal.Dialog
import io.kvision.panel.*
import io.kvision.state.ObservableValue
import io.kvision.state.bind
import io.kvision.utils.px
import kotlinx.browser.document

object MainPanel : SimplePanel() {
  val buttonPadding = 5.px
  val panel = SimplePanel()
  val userInfo = ObservableValue(UserInfo("", ""))

  init {
    add(panel)

    launchOrErrorToast(PAGE_NOT_LOADED) {
      userInfo.value = getUserInfo()
    }
  }

  fun refresh(slide: SlideData) = panel.displaySlide(slide)
}

/**
 * True while a click on a choice or a go-back is being handled: until the next slide is shown, the reason dialog is
 * cancelled, or the request fails. Those buttons are disabled meanwhile, so a double-click cannot record a choice twice
 * or open two reason dialogs.
 */
private val busy = ObservableValue(false)

/** Runs [action] on a click unless another click's action is still running (see [busy]); a failure shows a toast. */
private fun Button.onClickWhenIdle(action: suspend () -> Unit) {
  // Disposing the button (displaySlide's disposeAll) ends the subscription
  addBeforeDisposeHook(busy.subscribe { disabled = it })
  onClick {
    if (!busy.value) {
      busy.value = true
      launchOrErrorToast(CLICK_FAILED) {
        try {
          action()
        } finally {
          busy.value = false
        }
      }
    }
  }
}

private fun Container.displaySlide(slide: SlideData) {
  // disposeAll, not removeAll: disposing ends the old widgets' subscriptions (bind(userInfo), the busy flag), which
  // would otherwise keep every earlier slide's panels alive
  disposeAll()

  simplePanel {
    margin = 20.px
    width = 700.px

    simplePanel {
      flexPanel(FlexDirection.ROW, FlexWrap.WRAP, JustifyContent.SPACEBETWEEN, AlignItems.CENTER, spacing = 5) {
        paddingBottom = 10.px

        bind(userInfo, removeChildren = true) { user -> header(user, slide) }
      }
    }

    vPanel {
      slide.parentTitles
        .forEach { parentTitle ->
          button(parentTitle.title, style = SUCCESS) {
            paddingTop = 5.px
            paddingBottom = 5.px
            onClickWhenIdle { refresh(goBackInTime(parentTitle)) }
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
          onClickWhenIdle { refresh(goBackInTime(ParentTitle("/", "/"))) }
        }
      }
    } else {
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
      val icon = if (choice.alreadyVisited) "fas fa-bookmark" else "fas fa-angle-double-right"
      button(choice.choiceText, icon = icon, style = PRIMARY) {
        lowercase()
        onClickWhenIdle {
          if (choice.offset != 0) {
            refresh(goBackInTime(slide.goBackTarget(choice)))
          } else {
            val choiceReason = makeChoice(slide.pathName, slide.title, choice, slide.choices.size == 1)
            if (choiceReason.reason.isEmpty()) {
              promptUserForReason(slide.pathName, slide.title, choice)?.also { refresh(it) }
            } else {
              val newSlide = getCurrentSlide()
              refresh(newSlide)
            }
          }
        }
      }
    }
}

private fun String?.wordCount(): Int = this?.split(" ", "\n")?.filter { it.isNotEmpty() }?.size ?: 0

private const val MIN_REASON_WORDS = 5

/**
 * Asks why the student made [slideChoice] and records it, returning the next slide, or null if the student cancels.
 * If saving fails, a toast says so and the dialog opens again with the reason in it, so nothing typed is lost.
 */
private suspend fun promptUserForReason(
  fromPathName: String,
  fromTitle: String,
  slideChoice: SlideChoice,
): SlideData? {
  var draft: String? = null
  while (true) {
    // A new dialog each time: Bootstrap finishes hiding the last one ~0.5s later and its hidden event would close a
    // reopened one. Disposed once answered, or KVision's list of modals keeps every reason dialog for good.
    val reasonDialog = reasonDialog(slideChoice, draft)
    val reason =
      try {
        reasonDialog.getResult()
      } finally {
        reasonDialog.dispose()
      }
    if (reason.isNullOrBlank()) return null
    orErrorToast(REASON_NOT_SAVED) { provideReason(fromPathName, fromTitle, slideChoice, reason) }?.also { return it }
    draft = reason
  }
}

private fun reasonDialog(
  slideChoice: SlideChoice,
  draft: String?,
): Dialog<String> {
  val submit = Button("OK", disabled = true).apply {
    lowercase()
    verticalPadding(buttonPadding)
  }
  val wordCount = Span { float = PosFloat.RIGHT }
  return Dialog("Reasoning") {
    val input =
      TextArea(rows = 3, value = draft, label = """Reason for your "${slideChoice.choiceText}" decision:""") {
        placeholder = """ I chose "${slideChoice.choiceText}" because..."""
        autofocus = true
      }
    // The value follows the textarea's input event, so typing, pasting, dictation, and drag-and-drop all count
    addBeforeDisposeHook(
      input.subscribe { text ->
        val count = text.wordCount()
        submit.disabled = count < MIN_REASON_WORDS
        wordCount.content = if (count == 1) "1 word" else "$count words"
      },
    )
    add(input)
    add(wordCount)
    add(Span("Use at least $MIN_REASON_WORDS words.") { color = Color.name(Col.GRAY) })
    addButton(
      Button("Cancel", style = OUTLINESECONDARY).apply {
        lowercase()
        verticalPadding(buttonPadding)
        onClick { setResult("") }
      },
    )
    addButton(submit.apply { onClick { setResult(input.value) } })
//    onEvent {
//      keydown = {
//        if (it.keyCode == ENTER_KEY && input.value?.isNotEmpty() ?: false) {
//          setResult(input.value)
//        }
//      }
//    }
  }
}

private const val AVATAR_SIZE = 40

private fun Container.header(
  user: UserInfo,
  slide: SlideData,
) {
  val isTeacher = user.role == Role.TEACHER

  hPanel(alignItems = AlignItems.CENTER, spacing = 10) {
    avatar(user)
    vPanel {
      hPanel(spacing = 5) {
        span(user.fullName.ifBlank { user.email }) { fontWeight = FontWeight.BOLD }
        if (user.fullName.isNotBlank()) span(" · ${user.email}")
        if (isTeacher)
          span("teacher") {
            color = Color.name(Col.GRAY)
            fontStyle = FontStyle.ITALIC
          }
      }
      hPanel(spacing = 5) {
        +"Total Decisions: ${slide.decisionCount}"
        // The count is a bare text node, which the panel's spacing does not separate from the span, so the span keeps
        // its own distance
        if (isTeacher) {
          span("(your test walk, not counted in class progress)") {
            color = Color.name(Col.GRAY)
            marginLeft = 6.px
          }
        }
      }
      if (slide.displayConsecutiveCorrectDecisions)
        hPanel { +"Consecutive Correct Decisions: ${slide.consecutiveCorrectDecisions}" }
    }
  }

  hPanel(alignItems = AlignItems.CENTER) {
    if (isTeacher) {
      navLink("class summary", "fas fa-chart-bar") { document.location?.href = "/$SUMMARY" }
      navLink("student reasons", "fas fa-comments") { document.location?.href = "/$REASONS" }
    }
    navLink("logout", "fas fa-sign-out-alt") { postTo("/$LOGOUT") }
    // Clears the teacher's own test walk and starts it again from the first slide
    if (isTeacher && slide.showResetButton)
      navLink("reset", "fas fa-sync-alt") { document.location?.href = "/$USER_RESET" }
  }
}

private fun Container.navLink(
  label: String,
  icon: String,
  action: () -> Unit,
) {
  button(label, icon, style = LINK) {
    lowercase()
    onClick { action() }
  }
}

private fun Container.avatar(user: UserInfo) {
  val size = AVATAR_SIZE.px
  if (user.pictureUrl != null) {
    image(user.pictureUrl, user.fullName) {
      width = size
      height = size
      borderRadius = (AVATAR_SIZE / 2).px
      setAttribute("referrerpolicy", "no-referrer")
    }
  } else {
    div(initialsOf(user.fullName, user.email), align = Align.CENTER) {
      width = size
      height = size
      borderRadius = (AVATAR_SIZE / 2).px
      background = Background(Color.rgb(53, 121, 246))
      color = Color.name(Col.WHITE)
      fontWeight = FontWeight.BOLD
      display = Display.FLEX
      alignItems = AlignItems.CENTER
      justifyContent = JustifyContent.CENTER
    }
  }
}
