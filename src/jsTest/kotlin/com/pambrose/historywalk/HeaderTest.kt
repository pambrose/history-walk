package com.pambrose.historywalk

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotContainAnyOf
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.browser.window
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.asList

/**
 * The links in MainPanel's header, by the role the server reports. While teachers see the student view the server
 * reports a teacher as a student, so a teacher's page then has a student's header.
 */
class HeaderTest : StringSpec() {
  private val teacher = UserInfo("Grace Hopper", "ghopper@athenian.org", role = Role.TEACHER)

  private fun buttons() =
    MainPanel.element
      .querySelectorAll("button")
      .asList()
      .filterIsInstance<HTMLButtonElement>()
      .map { it.textContent.orEmpty().trim() }

  init {
    timeout = CLIENT_TEST_TIMEOUT_MS

    beforeSpec {
      FakeServer.reset()
      TestPage.root.add(MainPanel)
      // MainPanel asks for the user as it starts; its answer must not land on a test's user
      awaitUntil { MainPanel.userInfo.value != UserInfo("", "") }
    }

    afterSpec {
      MainPanel.userInfo.value = testStudent
      TestPage.root.remove(MainPanel)
    }

    "a teacher's header links to the admin page and the help, beside the class pages" {
      MainPanel.userInfo.value = teacher
      MainPanel.refresh(testSlide())

      buttons() shouldContainAll listOf("class summary", "student reasons", "admin", "help", "logout")
    }

    "a teacher's links, with the reset turned on, run from the class pages to help, then reset, then logout last" {
      MainPanel.userInfo.value = teacher
      MainPanel.refresh(testSlide().copy(showResetButton = true))
      val links = listOf("class summary", "student reasons", "admin", "help", "reset", "logout")

      buttons().filter { it in links } shouldBe links
    }

    "the reset link explains on mouse-over that it deletes only the teacher's own walk, without asking" {
      MainPanel.userInfo.value = teacher
      MainPanel.refresh(testSlide().copy(showResetButton = true))
      val tooltip = MainPanel.element.button("reset").title

      tooltip shouldContain "your own"
      tooltip shouldContain "first slide"
      tooltip shouldContain "Students' walks"
      tooltip shouldContain "doesn't ask"
    }

    "a teacher's help link opens the teacher guide in a new tab, leaving the walk open" {
      MainPanel.userInfo.value = teacher
      MainPanel.refresh(testSlide())
      val opened = mutableListOf<List<String?>>()
      val realOpen = window.asDynamic().open
      window.asDynamic().open = { url: String?, target: String?, features: String? ->
        opened += listOf(url, target, features)
        null
      }
      try {
        MainPanel.element.button("help").click()
      } finally {
        window.asDynamic().open = realOpen
      }

      opened shouldBe listOf(listOf("https://pambrose.github.io/history-walk/teachers/", "_blank", "noopener"))
    }

    "a student's header links to none of the teacher pages" {
      MainPanel.userInfo.value = testStudent
      MainPanel.refresh(testSlide())

      buttons() shouldContain "logout"
      buttons() shouldNotContainAnyOf listOf("class summary", "student reasons", "admin", "help")
    }
  }
}
