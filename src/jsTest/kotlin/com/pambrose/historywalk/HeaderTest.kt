package com.pambrose.historywalk

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotContainAnyOf
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

    "a teacher's header links to the admin page, beside the class pages" {
      MainPanel.userInfo.value = teacher
      MainPanel.refresh(testSlide())

      buttons() shouldContainAll listOf("class summary", "student reasons", "admin", "logout")
    }

    "a student's header links to none of the teacher pages" {
      MainPanel.userInfo.value = testStudent
      MainPanel.refresh(testSlide())

      buttons() shouldContain "logout"
      buttons() shouldNotContainAnyOf listOf("class summary", "student reasons", "admin")
    }
  }
}
