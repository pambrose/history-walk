package com.pambrose.historywalk

import com.pambrose.historywalk.ClientUtils.CLICK_FAILED
import com.pambrose.historywalk.ClientUtils.PAGE_NOT_LOADED
import com.pambrose.historywalk.ClientUtils.REASON_NOT_SAVED
import com.pambrose.historywalk.ClientUtils.launchOrErrorToast
import com.pambrose.historywalk.ClientUtils.lowercase
import com.pambrose.historywalk.ClientUtils.openInNewTab
import com.pambrose.historywalk.ClientUtils.orErrorToast
import com.pambrose.historywalk.ClientUtils.postTo
import com.pambrose.historywalk.ClientUtils.showErrorToast
import com.pambrose.historywalk.ClientUtils.verticalPadding
import com.pambrose.historywalk.Docs.TEACHER_GUIDE
import com.pambrose.historywalk.EndPoints.ADMIN
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
import dev.kilua.rpc.ServiceException
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
import kotlinx.browser.window
import org.w3c.dom.Storage

/** The page's content: it loads the signed-in user into [userInfo], for the header, and draws each slide in [panel]. */
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

  /** Draws [slide], with its header, in place of the slide shown. */
  fun refresh(slide: SlideData) = panel.displaySlide(slide)
}

/**
 * True while a click on a choice or a go-back is being handled: until the next slide is shown, the reason dialog is
 * cancelled, or the request fails. Those buttons are disabled meanwhile, so a double-click cannot record a choice twice
 * or open two reason dialogs.
 */
internal val busy = ObservableValue(false)

/** Runs [action] on a click unless another click's action is still running (see [busy]); a failure shows a toast. */
internal fun Button.onClickWhenIdle(action: suspend () -> Unit) {
  // Disposing the button (displaySlide's disposeAll) ends the subscription
  addBeforeDisposeHook(busy.subscribe { disabled = it })
  onClick {
    if (!busy.value) {
      busy.value = true
      launchOrErrorToast(CLICK_FAILED) {
        try {
          action()
        } catch (e: ServiceException) {
          // The server refused the move, most likely made from a page left behind in another tab: say why, and show
          // the slide the student is really on
          showErrorToast(e.message ?: CLICK_FAILED)
          MainPanel.refresh(getCurrentSlide())
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
              whileWritingReason { promptUserForReason(slide.pathName, slide.title, choice) }?.also { refresh(it) }
            } else {
              val newSlide = getCurrentSlide()
              refresh(newSlide)
            }
          }
        }
      }
    }
}

/**
 * True while the student is giving a reason: from the moment the reason dialog opens until the reason is saved or the
 * student cancels, a failed save and the dialog's reopening included. The version check holds its update dialog and
 * its reload back meanwhile (VersionCheckState), as a reload would throw away what the student typed.
 */
internal var writingReason = false
  private set

/** Runs [block], which asks for a reason and saves it, with [writingReason] set. */
private suspend fun <T> whileWritingReason(block: suspend () -> T): T {
  writingReason = true
  try {
    return block()
  } finally {
    writingReason = false
  }
}

/**
 * Asks why the student made [slideChoice] and records it, returning the next slide, or null if the student cancels.
 * If saving fails, a toast says so and the dialog opens again with the reason in it, so nothing typed is lost. The
 * reason is also kept in the tab ([ReasonDrafts]) from the moment it is sent until the server has it: if the session
 * has run out, the call waits behind the sign-in dialog for good, and signing in leaves the page, so the dialog for the
 * same move starts from it after the student is back.
 */
private suspend fun promptUserForReason(
  fromPathName: String,
  fromTitle: String,
  slideChoice: SlideChoice,
): SlideData? {
  val toPathName = slideChoice.toPathName
  var draft: String? = ReasonDrafts.get(fromPathName, toPathName)
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
    if (reason.isNullOrBlank()) {
      ReasonDrafts.drop(fromPathName, toPathName)
      return null
    }
    ReasonDrafts.keep(fromPathName, toPathName, reason)
    val next =
      try {
        orErrorToast(REASON_NOT_SAVED) { provideReason(fromPathName, fromTitle, slideChoice, reason) }
      } catch (e: ServiceException) {
        // Refused, so the server will not take it however often it is sent
        ReasonDrafts.drop(fromPathName, toPathName)
        throw e
      }
    if (next != null) {
      ReasonDrafts.drop(fromPathName, toPathName)
      return next
    }
    draft = reason
  }
}

/**
 * The reason a student sent for a move, kept in the tab's `sessionStorage`, which outlives the trip to Google sign-in
 * and back but not the tab, until the server has it. Where the browser refuses storage, nothing is kept.
 */
internal object ReasonDrafts {
  private const val PREFIX = "history-walk.reason-draft:"

  internal fun key(
    fromPathName: String,
    toPathName: String,
  ) = "$PREFIX$fromPathName -> $toPathName"

  fun get(
    fromPathName: String,
    toPathName: String,
  ): String? = storage { getItem(key(fromPathName, toPathName)) }

  fun keep(
    fromPathName: String,
    toPathName: String,
    reason: String,
  ) {
    storage { setItem(key(fromPathName, toPathName), reason) }
  }

  fun drop(
    fromPathName: String,
    toPathName: String,
  ) {
    storage { removeItem(key(fromPathName, toPathName)) }
  }

  private fun <T> storage(use: Storage.() -> T): T? =
    try {
      window.sessionStorage.use()
    } catch (e: Throwable) {
      console.log("Could not keep the reason in sessionStorage: $e")
      null
    }
}

internal fun reasonDialog(
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
        maxlength = ReasonRule.MAX_LENGTH
      }
    // The value follows the textarea's input event, so typing, pasting, dictation, and drag-and-drop all count
    addBeforeDisposeHook(
      input.subscribe { text ->
        // ReasonRule is the rule ContentService.provideReason enforces, so a reason the dialog accepts is not refused
        val count = ReasonRule.wordCount(text)
        submit.disabled = count < ReasonRule.MIN_WORDS
        wordCount.content = if (count == 1) "1 word" else "$count words"
      },
    )
    add(input)
    add(wordCount)
    add(Span("Use at least ${ReasonRule.MIN_WORDS} words.") { color = Color.name(Col.GRAY) })
    addButton(
      Button("Cancel", style = OUTLINESECONDARY).apply {
        lowercase()
        verticalPadding(buttonPadding)
        onClick { setResult("") }
      },
    )
    addButton(submit.apply { onClick { setResult(input.value) } })
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
      // The class pages open in new tabs, so the walk stays open beside them
      navLink("class summary", "fas fa-chart-bar") { openInNewTab("/$SUMMARY") }
      navLink("student reasons", "fas fa-comments") { openInNewTab("/$REASONS") }
      // The switch to the student view, and back. In the student view the server reports a teacher as a student, so
      // this link is not shown and the admin page says to come back to /admin
      navLink("admin", "fas fa-cog") { document.location?.href = "/$ADMIN" }
      // The teacher guide, in a new tab so the walk stays open
      navLink("help", "fas fa-question-circle") { openInNewTab(TEACHER_GUIDE) }
    }
    // Clears the teacher's own test walk and starts it again from the first slide, once the browser's confirmation is
    // answered OK, as the admin page's clear asks. A POST, as logout is: the server resets nothing on a GET, which any
    // link, image, or prefetch can send
    if (isTeacher && slide.showResetButton)
      navLink("reset", "fas fa-sync-alt", tooltip = RESET_TOOLTIP) {
        if (window.confirm(RESET_CONFIRMATION)) postTo("/$USER_RESET")
      }
    // Last, for everyone
    navLink("logout", "fas fa-sign-out-alt") { postTo("/$LOGOUT") }
  }
}

// What the reset link does, shown on mouse-over
internal const val RESET_TOOLTIP =
  "Start your own walk over: deletes every move and reason you have made, including any made in the student view, " +
    "and sends you back to the first slide. Students' walks and other teachers' walks are not touched. " +
    "It asks first."

// The confirmation the reset link asks for before it deletes anything
internal const val RESET_CONFIRMATION =
  "Start your own walk over? Every move and reason you have made will be deleted, including any made in the student " +
    "view, and you will go back to the first slide. Students' walks and other teachers' walks are kept."

// A header link: a link-styled button, with an optional tooltip (the title attribute, shown on mouse-over)
private fun Container.navLink(
  label: String,
  icon: String,
  tooltip: String? = null,
  action: () -> Unit,
) {
  button(label, icon, style = LINK) {
    lowercase()
    title = tooltip
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
