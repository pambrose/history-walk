package com.pambrose.historywalk

import com.pambrose.historywalk.PagesTest.Companion.links
import com.pambrose.historywalk.PagesTest.Companion.reasonsByUser
import com.pambrose.historywalk.PagesTest.Companion.tableRows
import com.pambrose.historywalk.Routes.appRoutes
import com.pambrose.historywalk.User.Companion.findCurrentSlideForUser
import com.pambrose.historywalk.auth.SignedNonceManager
import com.pambrose.historywalk.auth.assignUserSession
import com.pambrose.historywalk.auth.configureGoogleAuth
import com.pambrose.historywalk.auth.googleOAuthSettings
import com.pambrose.historywalk.auth.installUserSessions
import com.pambrose.historywalk.dbms.AdminTxs.clearTeacherWalks
import com.pambrose.historywalk.dbms.AdminTxs.countedTeacherMoves
import com.pambrose.historywalk.dbms.AdminTxs.setTeachersSeeStudentView
import com.pambrose.historywalk.dbms.AdminTxs.teachersSeeStudentView
import com.pambrose.historywalk.dbms.DbmsTxs.allSuccessUsers
import com.pambrose.historywalk.dbms.DbmsTxs.decisionCount
import com.pambrose.historywalk.dbms.DbmsTxs.updateLastSlide
import com.pambrose.historywalk.slides.SlideDeck
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Forbidden
import io.ktor.http.HttpStatusCode.Companion.Found
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.parameters
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

/**
 * The admin page's switch, which lets teachers walk as students do before the app is given to a class, and its clear
 * button. While teachers see the student view, each move a teacher makes is counted in the class pages like a
 * student's, and it stays counted after the switch goes back, until the clear deletes every teacher's walk.
 */
class AdminTest : StringSpec() {
  private lateinit var db: TestPostgres
  private lateinit var savedSlides: SlideDeck

  init {
    beforeSpec {
      db = TestPostgres()
      savedSlides = HistoryWalkServer.masterSlides
      HistoryWalkServer.masterSlides = testDeck
    }

    afterSpec {
      HistoryWalkServer.masterSlides = savedSlides
      db.close()
    }

    beforeTest { db.deleteAll() }

    "teachers see the teacher view until the switch is flipped, and again once it is flipped back" {
      teachersSeeStudentView().shouldBeFalse()
      setTeachersSeeStudentView(true)
      teachersSeeStudentView().shouldBeTrue()
      setTeachersSeeStudentView(false)
      teachersSeeStudentView().shouldBeFalse()
    }

    "a teacher's counted moves show in the class pages like a student's, and their other moves do not" {
      val student = student("27walker", "A Student")
      val trial = teacher("mstrial")
      val tester = teacher("mrtester")
      addChoice(student, START, MIDDLE, reason = "a student's reason")
      addChoice(trial, START, MIDDLE, reason = "a counted reason", countedAsStudent = true)
      addChoice(trial, MIDDLE, WIN, reason = "a counted win", countedAsStudent = true)
      addChoice(trial, START, LOSE, reason = "an uncounted reason")
      addChoice(tester, START, MIDDLE, reason = "a teacher's test")
      addChoice(tester, MIDDLE, WIN, reason = "a teacher's test win")

      // Each listed email with its decision count, and each email on the reasons page with its reasons
      val summary = Pages.displayUserSummary().tableRows().drop(1)
      summary.map { it[1] to it[2] } shouldContainExactlyInAnyOrder
        listOf("27walker@athenian.org" to "1", "mstrial@athenian.org" to "2")

      val reasons = Pages.displayUserReasons().reasonsByUser()
      reasons.associate { (email, rows) -> email to rows.drop(1).map { it.last() } } shouldBe
        mapOf(
          "27walker@athenian.org" to listOf("a student's reason"),
          "mstrial@athenian.org" to listOf("a counted reason", "a counted win"),
        )

      allSuccessUsers("Win").map { it.id } shouldContainExactly listOf(trial)
    }

    "countedTeacherMoves counts the moves teachers made in the student view" {
      val trial = teacher("mscount")
      addChoice(trial, START, MIDDLE, countedAsStudent = true)
      addChoice(trial, MIDDLE, WIN, countedAsStudent = true)
      addChoice(trial, START, LOSE)
      addChoice(student("27counted"), START, MIDDLE)

      countedTeacherMoves() shouldBe 2
    }

    "clearing deletes every teacher's moves and sends each teacher to the start, leaving students alone" {
      val student = student("27stays")
      val first = teacher("msfirst")
      val second = teacher("mrsecond")
      addChoice(student, START, MIDDLE)
      addChoice(first, START, MIDDLE, countedAsStudent = true)
      addChoice(first, MIDDLE, WIN, countedAsStudent = true)
      addChoice(second, START, LOSE)
      transaction {
        updateLastSlide(student, MIDDLE)
        updateLastSlide(first, WIN)
        updateLastSlide(second, LOSE)
      }

      clearTeacherWalks(setOf("msfirst@athenian.org", "mrsecond@athenian.org")) shouldBe 3

      decisionCount(first) shouldBe 0
      decisionCount(second) shouldBe 0
      findCurrentSlideForUser(first, testDeck) shouldBeSameInstanceAs testDeck.rootSlide
      findCurrentSlideForUser(second, testDeck) shouldBeSameInstanceAs testDeck.rootSlide
      decisionCount(student) shouldBe 1
      findCurrentSlideForUser(student, testDeck) shouldBeSameInstanceAs testDeck.findSlideByPathName(MIDDLE)
      countedTeacherMoves() shouldBe 0
    }

    // Someone taken off TEACHER_EMAILS still has the counted moves they made as a teacher, which are trial moves
    "clearing deletes counted moves even of someone no longer a teacher" {
      val former = teacher("msformer")
      addChoice(former, START, MIDDLE, countedAsStudent = true)
      addChoice(former, START, LOSE)

      clearTeacherWalks(emptySet()) shouldBe 1

      countedTeacherMoves() shouldBe 0
      decisionCount(former) shouldBe 1
    }

    "a teacher's admin page offers the student view, and the clear, while teachers see the teacher view" {
      val teacher = teacher(TEACHER)
      adminTest { client ->
        client.get("/test/sign-in/$teacher")
        client.get("/admin").run {
          status shouldBe OK
          contentType()?.withoutParameters() shouldBe ContentType.Text.Html
          headers[HttpHeaders.CacheControl] shouldBe "no-store, private"
          bodyAsText().run {
            // The page's links are separated by a middle dot
            this shouldContain "<meta charset=\"utf-8\">"
            this shouldContain "Teachers currently see the teacher view"
            buttons() shouldContainExactly listOf("Teachers see student view", "Clear teachers' trial walks")
          }
        }
      }
    }

    "the switch's button sets the view it names, and the page then offers the other one" {
      val teacher = teacher(TEACHER)
      adminTest { client ->
        client.get("/test/sign-in/$teacher")

        client.submitForm("/admin/view", parameters { append("view", "student") }).run {
          status shouldBe Found
          headers[HttpHeaders.Location] shouldBe "/admin"
          headers[HttpHeaders.CacheControl] shouldBe "no-store, private"
        }
        teachersSeeStudentView().shouldBeTrue()
        client.get("/admin").bodyAsText().run {
          this shouldContain "Teachers currently see the student view"
          buttons() shouldContainExactly listOf("Teachers see teacher view", "Clear teachers' trial walks")
        }

        // A second press of a page left open sets the same view again, rather than flipping it back
        client.submitForm("/admin/view", parameters { append("view", "student") })
        teachersSeeStudentView().shouldBeTrue()

        client.submitForm("/admin/view", parameters { append("view", "teacher") }).status shouldBe Found
        teachersSeeStudentView().shouldBeFalse()
      }
    }

    "the admin page's first link goes back to the walk, named by the view it will show" {
      val teacher = teacher(TEACHER)
      adminTest { client ->
        suspend fun firstLink(): Pair<String, String> {
          val page = client.get("/admin").bodyAsText()
          return page.links().first()
        }

        client.get("/test/sign-in/$teacher")
        firstLink() shouldBe ("/" to "Back to the walk (teacher view)")
        client.submitForm("/admin/view", parameters { append("view", "student") })
        firstLink() shouldBe ("/" to "Back to the walk (student view)")
      }
    }

    "a view the switch does not know is refused and changes nothing" {
      val teacher = teacher(TEACHER)
      adminTest { client ->
        client.get("/test/sign-in/$teacher")
        client.submitForm("/admin/view", parameters { append("view", "principal") }).status shouldBe BadRequest
        client.submitForm("/admin/view").status shouldBe BadRequest
      }
      teachersSeeStudentView().shouldBeFalse()
    }

    "the admin page counts the teachers' moves that the class pages count" {
      val teacher = teacher(TEACHER)
      addChoice(teacher, START, MIDDLE, countedAsStudent = true)
      addChoice(teacher, MIDDLE, WIN, countedAsStudent = true)
      adminTest { client ->
        client.get("/test/sign-in/$teacher")
        client.get("/admin").bodyAsText() shouldContain "Teacher moves counted in the class pages: 2"
      }
    }

    "the clear button clears every teacher's walk and comes back to the admin page" {
      val teacher = teacher(TEACHER)
      val student = student("27untouched")
      addChoice(teacher, START, MIDDLE, countedAsStudent = true)
      addChoice(student, START, MIDDLE)
      transaction { updateLastSlide(teacher, MIDDLE) }

      adminTest { client ->
        client.get("/test/sign-in/$teacher")
        client.submitForm("/admin/clear").run {
          status shouldBe Found
          headers[HttpHeaders.Location] shouldBe "/admin"
          headers[HttpHeaders.CacheControl] shouldBe "no-store, private"
        }
      }

      decisionCount(teacher) shouldBe 0
      findCurrentSlideForUser(teacher, testDeck) shouldBeSameInstanceAs testDeck.rootSlide
      decisionCount(student) shouldBe 1
    }

    "a student is refused the admin page and both of its buttons, which change nothing" {
      val student = student("27curious")
      val teacher = teacher(TEACHER)
      addChoice(teacher, START, MIDDLE)

      adminTest { client ->
        client.get("/test/sign-in/$student")
        client.get("/admin").status shouldBe Forbidden
        client.submitForm("/admin/view", parameters { append("view", "student") }).status shouldBe Forbidden
        client.submitForm("/admin/clear").status shouldBe Forbidden
      }

      teachersSeeStudentView().shouldBeFalse()
      decisionCount(teacher) shouldBe 1
    }

    "a signed-out visitor to the admin page or its buttons is sent to Google sign-in" {
      val teacher = teacher(TEACHER)
      addChoice(teacher, START, MIDDLE)

      adminTest { client ->
        listOf(
          client.get("/admin"),
          client.submitForm("/admin/view", parameters { append("view", "student") }),
          client.submitForm("/admin/clear"),
        ).forEach { r ->
          withClue(r.call.request.url.encodedPath) {
            r.status shouldBe Found
            Url(r.headers[HttpHeaders.Location]!!).host shouldBe "accounts.google.com"
          }
        }
      }

      teachersSeeStudentView().shouldBeFalse()
      decisionCount(teacher) shouldBe 1
    }

    // The page has no script of its own: the confirmation is the clear form's onsubmit
    "the clear button asks before it deletes anything" {
      val teacher = teacher(TEACHER)
      adminTest { client ->
        client.get("/test/sign-in/$teacher")
        client.get("/admin").bodyAsText().run {
          this shouldNotContain "<script"
          val clearForm = Regex("""<form[^>]*action="/admin/clear"[^>]*>""").find(this)!!.value
          clearForm shouldContain "onsubmit=\"return confirm("
        }
      }
    }
  }

  // The text of each button on the page, in page order
  private fun String.buttons() =
    Regex("<button[^>]*>(.*?)</button>", RegexOption.DOT_MATCHES_ALL)
      .findAll(this)
      .map { it.groupValues[1].replace("&#39;", "'").trim() }
      .toList()

  // The app's routes, with a test route that signs in any user id; redirects are returned, not followed
  private fun adminTest(block: suspend (HttpClient) -> Unit) =
    testApplication {
      val teachers = { setOf("$TEACHER@athenian.org") }
      application {
        installUserSessions(secure = false, teacherEmails = teachers)
        install(Authentication) {
          configureGoogleAuth("http://localhost/auth/callback") {
            googleOAuthSettings(
              clientId = "test-client",
              clientSecret = "test-secret",
              hostedDomain = "athenian.org",
              pkceKey = ByteArray(32),
              nonceManager = SignedNonceManager(ByteArray(32)),
            )
          }
        }
        routing {
          appRoutes(allowSlideAccess = false, teacherEmails = teachers)
          get("/test/sign-in/{id}") {
            call.assignUserSession(UUID.fromString(call.parameters["id"]!!))
            call.respondText("ok")
          }
        }
      }
      block(
        createClient {
          install(HttpCookies)
          followRedirects = false
        },
      )
    }

  /** The local part of the one address the spec's routes count as a teacher's. */
  companion object {
    const val TEACHER = "msadmin"
  }
}
