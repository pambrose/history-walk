package com.pambrose.historywalk

import com.pambrose.historywalk.ContentService.Companion.ADVANCE
import com.pambrose.historywalk.ContentService.Companion.NOT_A_CHOICE
import com.pambrose.historywalk.ContentService.Companion.NOT_BEHIND_YOU
import com.pambrose.historywalk.ContentService.Companion.NOT_ON_SLIDE
import com.pambrose.historywalk.ContentService.Companion.NO_REASON_NEEDED
import com.pambrose.historywalk.ContentService.Companion.SOMETHING_WENT_WRONG
import com.pambrose.historywalk.ReasonRule.MAX_LENGTH
import com.pambrose.historywalk.ReasonRule.MIN_WORDS
import com.pambrose.historywalk.auth.userUuid
import com.pambrose.historywalk.dbms.AdminTxs.setTeachersSeeStudentView
import com.pambrose.historywalk.dbms.DbmsTxs.allSuccessUsers
import com.pambrose.historywalk.dbms.DbmsTxs.correctDecisionStreak
import com.pambrose.historywalk.dbms.DbmsTxs.decisionCount
import com.pambrose.historywalk.dbms.DbmsTxs.updateLastSlide
import com.pambrose.historywalk.dbms.UserChoiceTable
import com.pambrose.historywalk.dbms.UsersTable
import com.pambrose.historywalk.slides.SlideDeck
import com.pambrose.historywalk.slides.SlideDeck.Companion.ROOT
import com.pambrose.historywalk.slides.SlideDeck.Companion.slideDeck
import dev.kilua.rpc.ServiceException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.ktor.server.application.ApplicationCall
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

/**
 * The RPC service against a real database (a [TestPostgres], so Docker), with the signed-in user's id supplied by a
 * mocked ApplicationCall. The choices sent are the ones the client builds from SlideData, unless a test is replaying a
 * forged call. Moves are checked against the deck and the user's stored slide: a move from elsewhere, a choice not
 * offered, a go-back to a slide not behind the user, or a reason too short or too long is refused, a repeated move
 * changes nothing, and a failure of the server's own reaches the browser without its message. While teachers see the
 * student view, a teacher is reported as a student and their moves are counted; a student's are never marked.
 */
class ContentServiceTest : StringSpec() {
  private lateinit var db: TestPostgres
  private lateinit var savedSlides: SlideDeck

  // The choices as slideData offers them: from /Start, and from /Start/Middle, whose go-back counts as a second choice
  private val goOn = SlideChoice("go on", MIDDLE, "Middle", deadEnd = false, offset = 0, alreadyVisited = false)
  private val goWrong = SlideChoice("go wrong", LOSE, "Lose", deadEnd = true, offset = 0, alreadyVisited = false)
  private val finish = SlideChoice("finish", WIN, "Win", deadEnd = false, offset = 0, alreadyVisited = false)
  private val startOver = SlideChoice("start over", "$MIDDLE/", "", deadEnd = true, offset = -1, alreadyVisited = false)

  private val reason = "it looked like the safest way"
  private val otherReason = "the other path seemed far too risky"

  // /Hall offers a single choice, which moves on without a reason, to /Hall/Door, the success slide
  private val hallDeck =
    slideDeck {
      val door = slide(id = 2, title = "Door", content = "Outside", success = true)
      slide(id = 1, title = "Hall", content = "A corridor", root = true) {
        choice("open the door", door)
      }
    }
  private val openDoor = SlideChoice("open the door", "/Hall/Door", "Door", false, offset = 0, alreadyVisited = false)

  // /Plan -> /Plan/Departure, whose one way on, "Continue", ends at /Plan/Departure/Caught, as Jermain's Autumn
  // Departure ends at the Paddy Rollers; or /Plan/Freedom, the success slide
  private val departureDeck =
    slideDeck {
      val freedom = slide(id = 3, title = "Freedom", content = "Free", success = true)
      val caught = slide(id = 4, title = "Caught", content = "THE END")
      val departure = slide(id = 2, title = "Departure", content = "A rustling...") { choice("Continue", caught) }
      slide(id = 1, title = "Plan", content = "When will you go?", root = true) {
        choice("leave in the autumn", departure)
        choice("leave in the winter", freedom)
      }
    }
  private val leaveInAutumn =
    SlideChoice("leave in the autumn", "/Plan/Departure", "Departure", false, offset = 0, alreadyVisited = false)

  private fun serviceFor(
    uuid: String,
    teachers: Set<String> = emptySet(),
  ): ContentService {
    val call = mockk<ApplicationCall>()
    every { call.userUuid } returns uuid
    return ContentService(call) { teachers }
  }

  // Each of the user's stored moves, by the title it leads to, and whether the class pages count it as a teacher's
  private fun storedCounted(uuid: String) =
    transaction {
      UserChoiceTable
        .selectAll()
        .where { UserChoiceTable.userUuidRef eq UUID.fromString(uuid) }
        .orderBy(UserChoiceTable.id)
        .map { it[UserChoiceTable.toTitle] to it[UserChoiceTable.countedAsStudent] }
    }

  private suspend fun <T> inStudentView(block: suspend () -> T): T {
    setTeachersSeeStudentView(true)
    return try {
      block()
    } finally {
      setTeachersSeeStudentView(false)
    }
  }

  private fun storedReasons(uuid: String) =
    transaction {
      UserChoiceTable
        .selectAll()
        .where { UserChoiceTable.userUuidRef eq UUID.fromString(uuid) }
        .map { it[UserChoiceTable.toTitle] to it[UserChoiceTable.reason] }
    }

  private fun storedPathName(uuid: String) =
    transaction {
      UsersTable
        .select(UsersTable.lastPathName)
        .where { UsersTable.id eq UUID.fromString(uuid) }
        .single()[UsersTable.lastPathName]
    }

  private fun placeAt(
    uuid: String,
    pathName: String,
  ) = transaction { updateLastSlide(uuid, pathName) }

  private suspend fun refusal(block: suspend () -> Unit) = shouldThrow<ServiceException> { block() }.message

  private suspend fun <T> withDeck(
    deck: SlideDeck,
    block: suspend () -> T,
  ): T {
    val saved = HistoryWalkServer.masterSlides
    HistoryWalkServer.masterSlides = deck
    return try {
      block()
    } finally {
      HistoryWalkServer.masterSlides = saved
    }
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
      placeAt(other, MIDDLE)

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

      service.provideReason(START, "Start", goOn, reason).run {
        pathName shouldBe MIDDLE
        decisionCount shouldBe 1
      }
      storedReasons(id) shouldBe listOf("Middle" to reason)
    }

    "a reason is stored without its surrounding whitespace" {
      val id = student("27trim")
      serviceFor(id).provideReason(START, "Start", goOn, "\n  $reason \n")
      storedReasons(id) shouldBe listOf("Middle" to reason)
    }

    "a choice made before returns its reason and moves the user on without asking again" {
      val id = student("27again")
      val service = serviceFor(id)
      service.provideReason(START, "Start", goOn, reason)
      service.goBackInTime(ParentTitle(START, "Start"))

      service.makeChoice(START, "Start", goOn, advance = false).reason shouldBe reason
      service.getCurrentSlide().pathName shouldBe MIDDLE
      decisionCount(id) shouldBe 1
    }

    "a slide's only choice advances without a reason and without being recorded" {
      withDeck(hallDeck) {
        val id = student("27advance")
        val service = serviceFor(id)

        service.makeChoice("/Hall", "Hall", openDoor, advance = true).reason shouldBe ADVANCE
        service.getCurrentSlide().pathName shouldBe "/Hall/Door"
        decisionCount(id) shouldBe 0
      }
    }

    "the server, not the client, decides that a slide's only choice advances" {
      withDeck(hallDeck) {
        val id = student("27advance2")
        val service = serviceFor(id)

        service.makeChoice("/Hall", "Hall", openDoor, advance = false).reason shouldBe ADVANCE
        service.getCurrentSlide().pathName shouldBe "/Hall/Door"
        decisionCount(id) shouldBe 0
      }
    }

    "a go-back choice counts, so a slide with one forward choice and a go-back asks for a reason" {
      val id = student("27noadvance")
      placeAt(id, MIDDLE)
      val service = serviceFor(id)

      // The client would send advance = false here; a forged true changes nothing
      service.makeChoice(MIDDLE, "Middle", finish, advance = true).reason shouldBe ""
      service.getCurrentSlide().pathName shouldBe MIDDLE
      decisionCount(id) shouldBe 0
    }

    "a choice that advances takes no reason, so none can be recorded for it" {
      withDeck(hallDeck) {
        val id = student("27noreason")
        refusal { serviceFor(id).provideReason("/Hall", "Hall", openDoor, reason) } shouldBe NO_REASON_NEEDED
        decisionCount(id) shouldBe 0
        storedPathName(id) shouldBe ROOT
      }
    }

    "a choice from a slide other than the user's current one is refused" {
      val id = student("27elsewhere")
      val service = serviceFor(id)

      refusal { service.makeChoice(MIDDLE, "Middle", finish, advance = false) } shouldBe NOT_ON_SLIDE
      refusal { service.provideReason(MIDDLE, "Middle", finish, reason) } shouldBe NOT_ON_SLIDE
      // The stored "/" is the root slide, whose path the client knows as /Start, never as "/"
      refusal { service.provideReason(ROOT, "Start", goOn, reason) } shouldBe NOT_ON_SLIDE
      decisionCount(id) shouldBe 0
      storedPathName(id) shouldBe ROOT
    }

    "a choice the current slide does not offer is refused" {
      val id = student("27nochoice")
      val service = serviceFor(id)

      // Straight from the start to the success slide
      val jump = finish.copy(choiceText = "go on")
      refusal { service.makeChoice(START, "Start", jump, advance = true) } shouldBe NOT_A_CHOICE
      refusal { service.provideReason(START, "Start", jump, reason) } shouldBe NOT_A_CHOICE
      refusal { service.provideReason(START, "Start", goOn.copy(toPathName = "/Start/Nowhere"), reason) } shouldBe
        NOT_A_CHOICE
      decisionCount(id) shouldBe 0
      storedPathName(id) shouldBe ROOT
    }

    "a go-back choice is not a forward choice, so it cannot be made or given a reason" {
      val id = student("27goback")
      placeAt(id, MIDDLE)
      val service = serviceFor(id)

      refusal { service.makeChoice(MIDDLE, "Middle", startOver, advance = false) } shouldBe NOT_A_CHOICE
      refusal { service.provideReason(MIDDLE, "Middle", startOver, reason) } shouldBe NOT_A_CHOICE
      decisionCount(id) shouldBe 0
      storedPathName(id) shouldBe MIDDLE
    }

    "the deck, not the client, supplies the titles, choice text and dead end that are stored" {
      val id = student("27liar")
      val service = serviceFor(id)

      // A forged call: the dead end dressed up as the success slide
      val forged = SlideChoice("I won", LOSE, "Win", deadEnd = false, offset = 0, alreadyVisited = true)
      service.provideReason(START, "Fake", forged, reason).pathName shouldBe LOSE

      transaction {
        UserChoiceTable
          .selectAll()
          .where { UserChoiceTable.userUuidRef eq UUID.fromString(id) }
          .single()
          .run {
            this[UserChoiceTable.fromTitle] shouldBe "Start"
            this[UserChoiceTable.toPathName] shouldBe LOSE
            this[UserChoiceTable.toTitle] shouldBe "Lose"
            this[UserChoiceTable.choiceText] shouldBe "go wrong"
            this[UserChoiceTable.deadEnd] shouldBe true
          }
      }
      allSuccessUsers("Win").map { it.id } shouldNotContain id
      correctDecisionStreak(id) shouldBe 0
    }

    "a decision whose one way on is a Continue to a dead end is offered and recorded as a dead end" {
      withDeck(departureDeck) {
        val id = student("27departure")
        val service = serviceFor(id)

        service.getCurrentSlide().choices.associate { it.choiceText to it.deadEnd } shouldBe
          mapOf("leave in the autumn" to true, "leave in the winter" to false)

        service.provideReason("/Plan", "Plan", leaveInAutumn, reason).pathName shouldBe "/Plan/Departure"
        transaction {
          UserChoiceTable
            .selectAll()
            .where { UserChoiceTable.userUuidRef eq UUID.fromString(id) }
            .single()[UserChoiceTable.deadEnd]
        } shouldBe true
        correctDecisionStreak(id) shouldBe 0

        // The Continue that follows is an advance, recorded nowhere, and ends at the dead end
        val carryOn = leaveInAutumn.copy(choiceText = "Continue", toPathName = "/Plan/Departure/Caught")
        service.makeChoice("/Plan/Departure", "Departure", carryOn, advance = true).reason shouldBe ADVANCE
        service.getCurrentSlide().pathName shouldBe "/Plan/Departure/Caught"
        decisionCount(id) shouldBe 1
      }
    }

    "a failure of the server's own reaches the browser as a plain failure, without its message" {
      // A session whose user is gone: the server's own error names the user, and the SQL error would quote the query
      val gone = UUID.randomUUID().toString()
      val service = serviceFor(gone)
      listOf<suspend () -> Unit>(
        { service.getCurrentSlide() },
        { service.makeChoice(START, "Start", goOn, advance = false) },
        { service.provideReason(START, "Start", goOn, reason) },
        { service.goBackInTime(ParentTitle(ROOT, "Start")) },
      ).forEach { call ->
        val failure = shouldThrow<IllegalStateException> { call() }
        failure.message shouldBe SOMETHING_WENT_WRONG
        failure.cause shouldBe null
      }
    }

    "makeChoice answers with the deck's version of the choice" {
      val id = student("27echo")
      val forged = goOn.copy(choiceText = "teleport", toTitle = "Win", deadEnd = true, offset = 3)

      serviceFor(id).makeChoice(START, "Fake", forged, advance = false) shouldBe
        UserChoice(START, "Start", goOn, "")
    }

    "a second reason for the same move keeps the first and gives the same result" {
      val id = student("27twice")
      val service = serviceFor(id)

      val first = service.provideReason(START, "Start", goOn, reason)
      // A second dialog for the same choice, submitted after the first moved the user on
      val second = service.provideReason(START, "Start", goOn, otherReason)

      second shouldBe first
      second.pathName shouldBe MIDDLE
      second.decisionCount shouldBe 1
      storedReasons(id) shouldBe listOf("Middle" to reason)
    }

    "a reason for a move already recorded, sent from its starting slide, keeps the first and moves the user on" {
      val id = student("27replay")
      val service = serviceFor(id)
      service.provideReason(START, "Start", goOn, reason)
      service.goBackInTime(ParentTitle(START, "Start"))

      service.provideReason(START, "Start", goOn, otherReason).run {
        pathName shouldBe MIDDLE
        decisionCount shouldBe 1
      }
      storedReasons(id) shouldBe listOf("Middle" to reason)
    }

    "a repeated choice for the move just made finds the user already there" {
      val id = student("27double")
      val service = serviceFor(id)
      service.provideReason(START, "Start", goOn, reason)

      service.makeChoice(START, "Start", goOn, advance = false).reason shouldBe reason
      service.getCurrentSlide().pathName shouldBe MIDDLE
      decisionCount(id) shouldBe 1
    }

    "after a decision, a different choice from the slide before is refused" {
      val id = student("27switch")
      val service = serviceFor(id)
      service.provideReason(START, "Start", goOn, reason)

      // Clicking "go on" and then "go wrong" before the page moved on
      refusal { service.provideReason(START, "Start", goWrong, otherReason) } shouldBe NOT_ON_SLIDE
      refusal { service.makeChoice(START, "Start", goWrong, advance = false) } shouldBe NOT_ON_SLIDE
      storedReasons(id) shouldBe listOf("Middle" to reason)
      storedPathName(id) shouldBe MIDDLE
    }

    "a move to the current slide that was never recorded is refused" {
      val id = student("27unrecorded")
      placeAt(id, MIDDLE)
      val service = serviceFor(id)

      refusal { service.provideReason(START, "Start", goOn, reason) } shouldBe NOT_ON_SLIDE
      refusal { service.makeChoice(START, "Start", goOn, advance = false) } shouldBe NOT_ON_SLIDE
      decisionCount(id) shouldBe 0
    }

    "a reason of fewer than five words is refused" {
      val id = student("27short")
      val service = serviceFor(id)

      refusal { service.provideReason(START, "Start", goOn, "it looked safe") } shouldBe
        "A reason needs at least $MIN_WORDS words"
      refusal { service.provideReason(START, "Start", goOn, "   \n  ") } shouldBe
        "A reason needs at least $MIN_WORDS words"
      decisionCount(id) shouldBe 0
      storedPathName(id) shouldBe ROOT
    }

    "an over-long reason is refused" {
      val id = student("27long")
      val tooLong = "word ".repeat(MAX_LENGTH / 5 + 1)

      refusal { serviceFor(id).provideReason(START, "Start", goOn, tooLong) } shouldBe
        "A reason can be at most $MAX_LENGTH characters"
      decisionCount(id) shouldBe 0
      storedPathName(id) shouldBe ROOT
    }

    "going back in time returns the user to an earlier slide" {
      val id = student("27back")
      val service = serviceFor(id)
      service.provideReason(START, "Start", goOn, reason)

      service.goBackInTime(ParentTitle(START, "Start")).pathName shouldBe START
      service.getCurrentSlide().pathName shouldBe START
    }

    "a go-back choice and a breadcrumb return to the ancestor they name" {
      val id = student("27goback2")
      placeAt(id, MIDDLE)
      val service = serviceFor(id)

      val middle = service.getCurrentSlide()
      service.goBackInTime(middle.goBackTarget(middle.choices.single { it.offset != 0 })).pathName shouldBe START

      placeAt(id, WIN)
      service.goBackInTime(service.getCurrentSlide().parentTitles.last()).pathName shouldBe MIDDLE
    }

    "going back to the start is allowed from anywhere" {
      val id = student("27home")
      placeAt(id, WIN)
      val service = serviceFor(id)

      service.goBackInTime(ParentTitle(ROOT, ROOT)).pathName shouldBe START
      storedPathName(id) shouldBe ROOT
      // Already there: still allowed
      service.goBackInTime(ParentTitle(ROOT, ROOT)).pathName shouldBe START
    }

    "going back to the current slide changes nothing" {
      val id = student("27stay")
      placeAt(id, MIDDLE)

      serviceFor(id).goBackInTime(ParentTitle(MIDDLE, "Middle")).pathName shouldBe MIDDLE
      storedPathName(id) shouldBe MIDDLE
    }

    "going back to a slide that is not behind the user is refused" {
      val id = student("27jump")
      placeAt(id, MIDDLE)
      val service = serviceFor(id)

      // Ahead, on another branch, and not in the deck at all
      refusal { service.goBackInTime(ParentTitle(WIN, "Win")) } shouldBe NOT_BEHIND_YOU
      refusal { service.goBackInTime(ParentTitle(LOSE, "Lose")) } shouldBe NOT_BEHIND_YOU
      refusal { service.goBackInTime(ParentTitle("/Start/Nowhere", "Nowhere")) } shouldBe NOT_BEHIND_YOU
      storedPathName(id) shouldBe MIDDLE
    }

    "one user's choices never touch another's" {
      val a = student("27alice")
      val b = student("27bob")
      serviceFor(a).provideReason(START, "Start", goOn, reason)

      storedReasons(b) shouldBe emptyList()
      serviceFor(b).getCurrentSlide().pathName shouldBe START
    }

    "user info comes from the signed-in user's row" {
      val id = student("27info", "Info Person")
      serviceFor(id).getUserInfo() shouldBe UserInfo("Info Person", "27info@athenian.org", null, Role.STUDENT)
    }

    "while teachers see the student view, a teacher's page is told the user is a student" {
      val id = teacher("mrviewer")
      val service = serviceFor(id, teachers = setOf("mrviewer@athenian.org"))

      service.getUserInfo().role shouldBe Role.TEACHER
      inStudentView { service.getUserInfo() } shouldBe UserInfo("Teacher", "mrviewer@athenian.org", null, Role.STUDENT)
      service.getUserInfo().role shouldBe Role.TEACHER
    }

    "a teacher's move counts in the class pages only while teachers see the student view" {
      val id = teacher("msmover")
      val service = serviceFor(id, teachers = setOf("msmover@athenian.org"))

      inStudentView { service.provideReason(START, "Start", goOn, reason) }
      service.provideReason(MIDDLE, "Middle", finish, otherReason)

      storedCounted(id) shouldBe listOf("Middle" to true, "Win" to false)
    }

    "a student's move is never marked as a teacher's, in either view" {
      val id = student("27either")
      val service = serviceFor(id)

      inStudentView { service.provideReason(START, "Start", goOn, reason) }
      service.provideReason(MIDDLE, "Middle", finish, otherReason)

      storedCounted(id) shouldBe listOf("Middle" to false, "Win" to false)
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
