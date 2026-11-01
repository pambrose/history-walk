package com.pambrose.historywalk

import com.pambrose.historywalk.ClientUtils.REASON_NOT_SAVED
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kvision.modal.Modal

/**
 * A click on a choice, through the real MainPanel and Rpc against [FakeServer]: makeChoice, then the reason dialog,
 * then provideReason, then the next slide.
 */
class ChoiceFlowTest : StringSpec() {
  private val start = testSlide("Start")
  private val north = testSlide("North", pathName = "/Start/North")
  private val reason = "the north road looked the safest"

  // makeChoice records the move and returns the reason given for it before, empty if none
  private fun answerChoiceWith(earlierReason: String) =
    FakeServer.on(IContentService::makeChoice) { call ->
      returns(UserChoice(start.pathName, start.title, call.param(2), earlierReason))
    }

  private suspend fun reasonDialogShowing(): Modal {
    awaitUntil { visibleModals().singleOrNull()?.getElement() != null }
    return visibleModals().single()
  }

  init {
    timeout = CLIENT_TEST_TIMEOUT_MS

    // FakeServer first: MainPanel asks for the user as it starts
    beforeSpec {
      FakeServer.reset()
      TestPage.root.add(MainPanel)
    }
    afterSpec { TestPage.root.remove(MainPanel) }
    beforeTest {
      FakeServer.reset()
      MainPanel.refresh(start)
    }
    afterTest {
      closeModals()
      busy.value = false
    }

    "a choice asks why, saves the reason, and shows the next slide" {
      answerChoiceWith("")
      FakeServer.on(IContentService::provideReason) { returns(north) }

      MainPanel.element.button("Go north").click()
      val dialog = reasonDialogShowing()
      dialog.element.textArea.enter(reason)
      dialog.element.button("OK").click()
      awaitUntil { "North" in MainPanel.panel.texts() }

      val made = FakeServer.callsTo(IContentService::makeChoice).single()
      made.param<String>(0) shouldBe start.pathName
      made.param<SlideChoice>(2).choiceText shouldBe "Go north"
      made.param<Boolean>(3) shouldBe false
      val saved = FakeServer.callsTo(IContentService::provideReason).single()
      saved.param<SlideChoice>(2).choiceText shouldBe "Go north"
      saved.param<String>(3) shouldBe reason
    }

    "a reason that could not be saved is offered again in a new dialog, so nothing typed is lost" {
      answerChoiceWith("")
      var saves = 0
      FakeServer.on(IContentService::provideReason) {
        if (++saves == 1) Reply.HttpError(503, "Service Unavailable") else returns(north)
      }

      MainPanel.element.button("Go north").click()
      val first = reasonDialogShowing()
      first.element.textArea.enter(reason)
      toastsShownBy {
        first.element.button("OK").click()
        awaitUntil { visibleModals().singleOrNull().let { it != null && it !== first && it.getElement() != null } }
      } shouldBe listOf(REASON_NOT_SAVED)

      val second = reasonDialogShowing()
      second.element.textArea.value shouldBe reason
      second.element.button("OK").click()
      awaitUntil { "North" in MainPanel.panel.texts() }

      FakeServer.callsTo(IContentService::provideReason).map { it.param<String>(3) } shouldBe listOf(reason, reason)
    }

    "cancelling the reason leaves the student on the slide, with the choices enabled" {
      answerChoiceWith("")

      MainPanel.element.button("Go north").click()
      reasonDialogShowing().element.button("Cancel").click()
      awaitUntil { !busy.value }

      MainPanel.panel.texts() shouldContain "Start"
      FakeServer.callsTo(IContentService::provideReason).shouldBeEmpty()
      MainPanel.element.button("Go north").disabled shouldBe false
    }

    "a choice already explained moves straight on, without asking again" {
      answerChoiceWith("an earlier reason, given last time")
      FakeServer.on(IContentService::getCurrentSlide) { returns(north) }

      MainPanel.element.button("Go north").click()
      awaitUntil { "North" in MainPanel.panel.texts() }

      visibleModals().shouldBeEmpty()
      FakeServer.callsTo(IContentService::provideReason).shouldBeEmpty()
    }

    "a go-back returns to the ancestor it names, without asking why" {
      val town = ParentTitle("/Start", "Start")
      val house = testSlide("House", listOf(testChoice("Try another house", offset = -1)), "/Start/House", listOf(town))
      FakeServer.on(IContentService::goBackInTime) { returns(start) }
      MainPanel.refresh(house)

      MainPanel.element.button("Try another house").click()
      awaitUntil { "Start" in MainPanel.panel.texts() && !busy.value }

      FakeServer.callsTo(IContentService::goBackInTime).single().param<ParentTitle>(0) shouldBe town
      FakeServer.callsTo(IContentService::makeChoice) shouldHaveSize 0
      visibleModals().shouldBeEmpty()
    }
  }
}
