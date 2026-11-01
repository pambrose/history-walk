package com.pambrose.historywalk

import com.pambrose.historywalk.User.Companion.findCurrentSlideForUser
import com.pambrose.historywalk.User.Companion.userExists
import com.pambrose.historywalk.dbms.DbmsTxs.allSuccessUsers
import com.pambrose.historywalk.dbms.DbmsTxs.allUserReasons
import com.pambrose.historywalk.dbms.DbmsTxs.allUserSummaries
import com.pambrose.historywalk.dbms.DbmsTxs.correctDecisionStreak
import com.pambrose.historywalk.dbms.DbmsTxs.decisionCount
import com.pambrose.historywalk.dbms.DbmsTxs.deleteChoices
import com.pambrose.historywalk.dbms.DbmsTxs.slideData
import com.pambrose.historywalk.dbms.DbmsTxs.updateLastSlide
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.sql.SQLException
import java.util.UUID

class DbmsTxsTest : StringSpec() {
  private lateinit var db: TestPostgres

  init {
    beforeSpec { db = TestPostgres() }
    afterSpec { db.close() }
    beforeTest { db.deleteAll() }

    "a new user starts at the root slide" {
      val id = student("27new")
      findCurrentSlideForUser(id, testDeck) shouldBeSameInstanceAs testDeck.rootSlide
    }

    "updateLastSlide moves the user to that slide" {
      val id = student("27mover")
      transaction { updateLastSlide(id, MIDDLE) }
      findCurrentSlideForUser(id, testDeck) shouldBeSameInstanceAs testDeck.findSlideByPathName(MIDDLE)
    }

    "a stored path the deck no longer has falls back to the root slide" {
      val id = student("27renamed")
      transaction { updateLastSlide(id, "/Start/Renamed") }
      findCurrentSlideForUser(id, testDeck) shouldBeSameInstanceAs testDeck.rootSlide
    }

    "updateLastSlide refuses an unknown user" {
      shouldThrow<IllegalStateException> {
        transaction { updateLastSlide(UUID.randomUUID().toString(), MIDDLE) }
      }.message shouldContain "Missing uuid"
    }

    "findCurrentSlideForUser refuses an unknown user" {
      shouldThrow<IllegalStateException> {
        findCurrentSlideForUser(UUID.randomUUID().toString(), testDeck)
      }.message shouldContain "Missing uuid"
    }

    "decisionCount counts only the user's own choices" {
      val a = student("27alpha")
      val b = student("27beta")
      addChoice(a, START, MIDDLE)
      addChoice(a, MIDDLE, WIN)
      addChoice(b, START, LOSE, deadEnd = true)
      decisionCount(a) shouldBe 2
      decisionCount(b) shouldBe 1
      decisionCount(student("27gamma")) shouldBe 0
    }

    "with no wrong choices, the streak counts every choice" {
      val id = student("27perfect")
      addChoice(id, START, MIDDLE, at = time(1))
      addChoice(id, MIDDLE, WIN, at = time(2))
      correctDecisionStreak(id) shouldBe 2
    }

    "the streak counts the choices made since the last wrong one" {
      val id = student("27streak")
      addChoice(id, START, MIDDLE, at = time(1))
      addChoice(id, START, LOSE, deadEnd = true, at = time(2))
      addChoice(id, MIDDLE, "/Start/Middle/Wrong", deadEnd = true, at = time(3))
      addChoice(id, MIDDLE, WIN, at = time(4))
      addChoice(id, "/Start/Middle/Win", "/Start/Middle/Win/Again", at = time(5))
      correctDecisionStreak(id) shouldBe 2
    }

    "the streak is zero right after a wrong choice" {
      val id = student("27oops")
      addChoice(id, START, MIDDLE, at = time(1))
      addChoice(id, START, LOSE, deadEnd = true, at = time(2))
      correctDecisionStreak(id) shouldBe 0
    }

    "deleteChoices removes only that user's choices" {
      val a = student("27reset")
      val b = student("27keep")
      addChoice(a, START, MIDDLE)
      addChoice(a, MIDDLE, WIN)
      addChoice(b, START, MIDDLE)
      transaction { deleteChoices(a) }
      decisionCount(a) shouldBe 0
      decisionCount(b) shouldBe 1
    }

    "the same move cannot be recorded twice for a user" {
      val id = student("27twice")
      addChoice(id, START, MIDDLE)
      shouldThrow<SQLException> { addChoice(id, START, MIDDLE) }
      decisionCount(id) shouldBe 1
    }

    "slideData describes the slide, its choices, and the user's progress" {
      val id = student("27walker")
      addChoice(id, START, MIDDLE)
      addChoice(id, MIDDLE, WIN)

      slideData(id, testDeck.findSlideByPathName(MIDDLE)).run {
        pathName shouldBe MIDDLE
        title shouldBe "Middle"
        content shouldContain "Keep going"
        success.shouldBeFalse()
        verticalChoices.shouldBeFalse()
        parentTitles shouldContainExactly listOf(ParentTitle(START, "Start"))
        choices shouldContainExactly
          listOf(
            SlideChoice("finish", WIN, "Win", deadEnd = false, offset = 0, alreadyVisited = true),
            SlideChoice("start over", "$MIDDLE/", "", deadEnd = true, offset = -1, alreadyVisited = false),
          )
        decisionCount shouldBe 2
        consecutiveCorrectDecisions shouldBe 2
        displayConsecutiveCorrectDecisions.shouldBeTrue()
        showResetButton.shouldBeFalse()
      }
    }

    "slideData marks a choice that leads to a slide without choices as a dead end" {
      val id = student("27root")
      slideData(id, testDeck.rootSlide).run {
        parentTitles.shouldBeEmpty()
        choices.associate { it.toTitle to it.deadEnd } shouldBe mapOf("Middle" to false, "Lose" to true)
        choices.none { it.alreadyVisited }.shouldBeTrue()
        decisionCount shouldBe 0
      }
    }

    "slideData renders the slide's markdown" {
      val id = student("27winner")
      slideData(id, testDeck.findSlideByPathName(WIN)).run {
        success.shouldBeTrue()
        content shouldContain "<strong>won</strong>"
        parentTitles.map { it.title } shouldContainExactly listOf("Start", "Middle")
      }
    }

    "the summaries list students with their decision counts and leave teachers out" {
      val busy = student("27busy")
      val idle = student("27idle")
      val teacher = teacher("mrteacher")
      addChoice(busy, START, MIDDLE)
      addChoice(busy, MIDDLE, WIN)
      addChoice(teacher, START, MIDDLE)
      transaction { updateLastSlide(busy, WIN) }

      allUserSummaries()
        .associate { it.id to (it.lastPathName to it.decisionCount) } shouldBe
        mapOf(
          busy to (WIN to 2),
          idle to ("/" to 0),
        )
    }

    "the reasons list each student's choices with names and leave teachers out" {
      val id = student("27reasons", "Rea Son")
      val teacher = teacher("msreasons")
      addChoice(id, START, MIDDLE, reason = "it looked safe")
      addChoice(teacher, START, MIDDLE, reason = "testing")

      allUserReasons().map { listOf(it.id, it.fullName, it.email, it.fromTitle, it.toTitle, it.reason) } shouldBe
        listOf(listOf(id, "Rea Son", "27reasons@athenian.org", "Start", "Middle", "it looked safe"))
    }

    "the success list holds only the students who reached the success slide" {
      val winner = student("27won")
      val loser = student("27lost")
      val teacher = teacher("mrwinner")
      addChoice(winner, MIDDLE, WIN)
      addChoice(loser, START, LOSE, deadEnd = true)
      addChoice(teacher, MIDDLE, WIN)

      allSuccessUsers("Win").map { it.id } shouldContainExactlyInAnyOrder listOf(winner)
    }

    "a user is loaded by id, and is gone once deleted" {
      val id = UUID.fromString(student("27gone", "Gone Soon"))
      User(id, true).run {
        email.value shouldBe "27gone@athenian.org"
        fullName.value shouldBe "Gone Soon"
        userExists(id).shouldBeTrue()
        deleteUser()
      }
      userExists(id).shouldBeFalse()
    }

    "loading a user who does not exist fails" {
      shouldThrow<IllegalStateException> { User(UUID.randomUUID(), true) }.message shouldContain "not found"
    }
  }
}
