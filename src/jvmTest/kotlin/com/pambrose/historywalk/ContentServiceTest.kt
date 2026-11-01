package com.pambrose.historywalk

import com.pambrose.historywalk.auth.userUuid
import com.pambrose.historywalk.dbms.DbmsTxs.decisionCount
import com.pambrose.historywalk.dbms.DbmsTxs.updateLastSlide
import com.pambrose.historywalk.dbms.UserChoiceTable
import com.pambrose.historywalk.slides.SlideDeck
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.server.application.ApplicationCall
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

// The RPC service against a real database, with the signed-in user's id supplied by a mocked ApplicationCall
class ContentServiceTest : StringSpec() {
  private lateinit var db: TestPostgres
  private lateinit var savedSlides: SlideDeck

  private val goOn = SlideChoice("go on", MIDDLE, "Middle", deadEnd = false, offset = 0, alreadyVisited = false)
  private val finish = SlideChoice("finish", WIN, "Win", deadEnd = false, offset = 0, alreadyVisited = false)

  private fun serviceFor(uuid: String): ContentService {
    val call = mockk<ApplicationCall>()
    every { call.userUuid } returns uuid
    return ContentService(call)
  }

  private fun storedReasons(uuid: String) =
    transaction {
      UserChoiceTable
        .selectAll()
        .where { UserChoiceTable.userUuidRef eq UUID.fromString(uuid) }
        .map { it[UserChoiceTable.toTitle] to it[UserChoiceTable.reason] }
    }

  init {
    beforeSpec {
      db = TestPostgres()
      mockkStatic("com.pambrose.historywalk.auth.UserSessionKt")
      savedSlides = HistoryWalkServer.masterSlides
      HistoryWalkServer.masterSlides = testDeck
    }

    afterSpec {
      HistoryWalkServer.masterSlides = savedSlides
      unmockkStatic("com.pambrose.historywalk.auth.UserSessionKt")
      db.close()
    }

    "the current slide is the signed-in user's" {
      val walker = student("27current")
      val other = student("27other")
      transaction { updateLastSlide(other, MIDDLE) }

      serviceFor(walker).getCurrentSlide().pathName shouldBe START
      serviceFor(other).getCurrentSlide().pathName shouldBe MIDDLE
    }

    "a first choice asks for a reason and leaves the user where they are" {
      val id = student("27first")
      val service = serviceFor(id)

      service.makeChoice(START, "Start", goOn, advance = false).reason shouldBe ""
      service.getCurrentSlide().pathName shouldBe START
      decisionCount(id) shouldBe 0
    }

    "giving a reason records the choice and moves the user on" {
      val id = student("27reason")
      val service = serviceFor(id)

      service.provideReason(START, "Start", goOn, "it looked safe").run {
        pathName shouldBe MIDDLE
        decisionCount shouldBe 1
      }
      storedReasons(id) shouldBe listOf("Middle" to "it looked safe")
    }

    "a choice made before returns its reason and moves the user on without asking again" {
      val id = student("27again")
      val service = serviceFor(id)
      service.provideReason(START, "Start", goOn, "first time")
      service.goBackInTime(ParentTitle(START, "Start"))

      service.makeChoice(START, "Start", goOn, advance = false).reason shouldBe "first time"
      service.getCurrentSlide().pathName shouldBe MIDDLE
      decisionCount(id) shouldBe 1
    }

    "a slide's only choice advances without a reason and without being recorded" {
      val id = student("27advance")
      transaction { updateLastSlide(id, MIDDLE) }
      val service = serviceFor(id)

      service.makeChoice(MIDDLE, "Middle", finish, advance = true).reason shouldBe "Advance"
      service.getCurrentSlide().pathName shouldBe WIN
      decisionCount(id) shouldBe 0
    }

    "going back in time returns the user to an earlier slide" {
      val id = student("27back")
      val service = serviceFor(id)
      service.provideReason(START, "Start", goOn, "onward")

      service.goBackInTime(ParentTitle(START, "Start")).pathName shouldBe START
      service.getCurrentSlide().pathName shouldBe START
    }

    "one user's choices never touch another's" {
      val a = student("27alice")
      val b = student("27bob")
      serviceFor(a).provideReason(START, "Start", goOn, "alice's reason")

      storedReasons(b) shouldBe emptyList()
      serviceFor(b).getCurrentSlide().pathName shouldBe START
    }

    "user info comes from the signed-in user's row" {
      val id = student("27info", "Info Person")
      serviceFor(id).getUserInfo() shouldBe UserInfo("Info Person", "27info@athenian.org", null, Role.STUDENT)
    }

    "user info for an id with no row is Unknown" {
      serviceFor(UUID.randomUUID().toString()).getUserInfo() shouldBe UserInfo("Unknown", "Unknown")
    }

    "the service reads the user from the call, every time" {
      val id = student("27calls")
      val call = mockk<ApplicationCall>()
      every { call.userUuid } returns id
      val service = ContentService(call)
      service.getCurrentSlide()
      service.getCurrentSlide()
      verify(exactly = 2) { call.userUuid }
    }
  }
}
