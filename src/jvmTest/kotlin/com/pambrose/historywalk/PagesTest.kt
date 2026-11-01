package com.pambrose.historywalk

import com.pambrose.historywalk.Pages.displayAllSlides
import com.pambrose.historywalk.Pages.displayUserReasons
import com.pambrose.historywalk.Pages.displayUserSummary
import com.pambrose.historywalk.Routes.appRoutes
import com.pambrose.historywalk.User.Companion.findCurrentSlideForUser
import com.pambrose.historywalk.auth.SignedNonceManager
import com.pambrose.historywalk.auth.assignUserSession
import com.pambrose.historywalk.auth.configureGoogleAuth
import com.pambrose.historywalk.auth.googleOAuthSettings
import com.pambrose.historywalk.auth.installUserSessions
import com.pambrose.historywalk.dbms.DbmsTxs.updateLastSlide
import com.pambrose.historywalk.slides.SlideDeck
import com.pambrose.historywalk.slides.SlideDeck.Companion.slideDeck
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders.CacheControl
import io.ktor.http.HttpHeaders.Location
import io.ktor.http.HttpStatusCode.Companion.Found
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.contentType
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

// The teacher pages, rendered from a real database, and the teacher routes that serve them
class PagesTest : StringSpec() {
  private lateinit var db: TestPostgres
  private lateinit var savedSlides: SlideDeck

  init {
    beforeSpec {
      db = TestPostgres()
      savedSlides = HistoryWalkServer.masterSlides
      HistoryWalkServer.masterSlides = pagesDeck
    }

    afterSpec {
      HistoryWalkServer.masterSlides = savedSlides
      db.close()
    }

    beforeTest { db.deleteAll() }

    "the summary lists each student's name, email, decisions, success, and last slide" {
      val maya = student("27maya", "Maya Smith")
      addChoice(maya, TOWN, HUT)
      addChoice(maya, HUT, "$HUT/Win")
      transaction { updateLastSlide(maya, "$HUT/Win") }

      displayUserSummary().tableRows() shouldBe
        listOf(
          listOf("Name", "Email", "Decisions", "Success", "Last Slide"),
          listOf("Maya Smith", "27maya@athenian.org", "2", "true", "$HUT/Win"),
        )
    }

    "the summary lists students by decision count, fewest first" {
      val busy = student("27busy", "Busy")
      student("27idle", "Idle")
      val some = student("27some", "Some")
      addChoice(busy, TOWN, HUT)
      addChoice(busy, HUT, "$HUT/Win")
      addChoice(busy, TOWN, HUT_AGAIN)
      addChoice(some, TOWN, HUT)

      displayUserSummary().tableRows().drop(1).map { it[0] to it[2] } shouldContainExactly
        listOf("Idle" to "0", "Some" to "1", "Busy" to "3")
    }

    "a student succeeds by reaching a slide with the deck's success title, through any copy of it" {
      val viaCopy = student("27copy", "Via Copy")
      val stuck = student("27stuck", "Stuck")
      addChoice(viaCopy, HUT_AGAIN, "$HUT_AGAIN/Win")
      addChoice(stuck, TOWN, HUT)

      displayUserSummary().tableRows().drop(1).associate { it[0] to it[3] } shouldBe
        mapOf("Via Copy" to "true", "Stuck" to "false")
    }

    "the summary leaves teachers out" {
      student("27only", "Only Student")
      addChoice(teacher("mrsummary"), TOWN, HUT)

      displayUserSummary().tableRows().drop(1).map { it[0] } shouldContainExactly listOf("Only Student")
    }

    "the summary escapes a name, so it cannot add markup to the page" {
      student("27markup", "<b>Bold</b> Name")

      displayUserSummary().run {
        this shouldNotContain "<b>Bold</b>"
        tableRows()[1][0] shouldBe "<b>Bold</b> Name"
      }
    }

    "with no students, the summary has only its header row" {
      displayUserSummary().tableRows() shouldBe listOf(listOf("Name", "Email", "Decisions", "Success", "Last Slide"))
    }

    "the reasons page lists each student's reasons under their email, in the order they were given" {
      val bob = student("27bob")
      val alice = student("27alice")
      addChoice(bob, TOWN, HUT, reason = "bob's first", at = time(1))
      addChoice(alice, TOWN, HUT_AGAIN, reason = "alice's only", at = time(2))
      addChoice(bob, HUT, "$HUT/Win", reason = "bob's second", at = time(3))

      displayUserReasons().reasonsByUser() shouldBe
        listOf(
          "27alice@athenian.org" to
            listOf(listOf("From/To", "Reason"), listOf("Town -> Hut again", "alice's only")),
          "27bob@athenian.org" to
            listOf(
              listOf("From/To", "Reason"),
              listOf("Town -> Hut", "bob's first"),
              listOf("Hut -> Win", "bob's second"),
            ),
        )
    }

    "the reasons page leaves teachers out" {
      addChoice(student("27reasoner"), TOWN, HUT, reason = "a student's reason")
      addChoice(teacher("msreasons"), TOWN, HUT, reason = "a teacher's test")

      displayUserReasons().reasonsByUser().map { it.first } shouldContainExactly listOf("27reasoner@athenian.org")
    }

    // A reason is typed by a student and shown to a teacher, so it must arrive as text, never as markup
    "the reasons page escapes a reason, so a student cannot add markup or script to it" {
      val reason = """<img src=x onerror="alert(1)"><script>alert(2)</script>"""
      addChoice(student("27sneaky"), TOWN, HUT, reason = reason)

      displayUserReasons().run {
        this shouldNotContain "<img"
        this shouldNotContain "<script"
        reasonsByUser().single().second[1][1] shouldBe reason
      }
    }

    "with no reasons, the reasons page lists no one" {
      student("27quiet")
      displayUserReasons().run {
        reasonsByUser() shouldBe emptyList()
        this shouldNotContain "<table"
      }
    }

    "the slides page lists each slide id in order, with a link to each of its instances" {
      displayAllSlides().tableRows() shouldBe
        listOf(
          listOf("ID", "Title", "Instances"),
          listOf("1:", "Town", "1"),
          listOf("2:", "Hut", "1 2"),
          listOf("3:", "Win", "1 2"),
        )
      displayAllSlides().links() shouldContainExactly
        listOf(
          "/slide/1" to "Town",
          "/slide/1/0" to "1",
          "/slide/2" to "Hut",
          "/slide/2/0" to "1",
          "/slide/2/1" to "2",
          "/slide/3" to "Win",
          "/slide/3/0" to "1",
          "/slide/3/1" to "2",
        )
    }

    "a teacher gets the summary and reasons pages as HTML" {
      val maya = student("27routed", "Routed Student")
      addChoice(maya, TOWN, HUT, reason = "through the route")
      val teacher = teacher(TEACHER)

      pagesTest { client ->
        client.get("/test/sign-in/$teacher")
        client.get("/summary").run {
          status shouldBe OK
          contentType()?.withoutParameters() shouldBe ContentType.Text.Html
          headers[CacheControl] shouldBe "no-store, private"
          bodyAsText() shouldContain "Routed Student"
        }
        client.get("/reasons").run {
          status shouldBe OK
          contentType()?.withoutParameters() shouldBe ContentType.Text.Html
          headers[CacheControl] shouldBe "no-store, private"
          bodyAsText() shouldContain "through the route"
        }
      }
    }

    "a teacher gets the slides page when slide access is allowed, and a 404 when it is not" {
      val teacher = teacher(TEACHER)
      pagesTest(allowSlideAccess = true) { client ->
        client.get("/test/sign-in/$teacher")
        client.get("/slides").run {
          status shouldBe OK
          bodyAsText() shouldContain "/slide/2/1"
        }
      }
      pagesTest(allowSlideAccess = false) { client ->
        client.get("/test/sign-in/$teacher")
        client.get("/slides").status shouldBe NotFound
        client.get("/slide/2").status shouldBe NotFound
      }
    }

    "a slide link moves the teacher to that instance of the slide" {
      val teacher = teacher(TEACHER)
      pagesTest(allowSlideAccess = true) { client ->
        client.get("/test/sign-in/$teacher")

        client.get("/slide/2/1").run {
          status shouldBe Found
          headers[Location] shouldBe "/"
          headers[CacheControl] shouldBe "no-store, private"
        }
        findCurrentSlideForUser(teacher, pagesDeck).pathName shouldBe HUT_AGAIN

        client.get("/slide/2").status shouldBe Found
        findCurrentSlideForUser(teacher, pagesDeck).pathName shouldBe HUT
      }
    }

    "a link to a slide that does not exist says so and leaves the teacher where they are" {
      val teacher = teacher(TEACHER)
      transaction { updateLastSlide(teacher, HUT) }
      pagesTest(allowSlideAccess = true) { client ->
        client.get("/test/sign-in/$teacher")
        client.get("/slide/9").bodyAsText() shouldBe "Slide not found: 9/0"
        client.get("/slide/2/5").bodyAsText() shouldBe "Slide not found: 2/5"
      }
      findCurrentSlideForUser(teacher, pagesDeck).pathName shouldBe HUT
    }
  }

  // The app's routes, with a test route that signs in any user id; redirects are returned, not followed
  private fun pagesTest(
    allowSlideAccess: Boolean = true,
    block: suspend (HttpClient) -> Unit,
  ) = testApplication {
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
        appRoutes(allowSlideAccess = allowSlideAccess, teacherEmails = teachers)
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

  companion object {
    const val TEACHER = "mrpages"
    const val TOWN = "/Town"
    const val HUT = "/Town/Hut"
    const val HUT_AGAIN = "/Town/Hut again"

    // Town -> Hut -> Win, and a copy of the Hut, with its own copy of Win, so ids 2 and 3 each have two instances
    val pagesDeck =
      slideDeck {
        val win = slide(id = 3, title = "Win", content = "w", success = true)
        val hut =
          slide(id = 2, title = "Hut", content = "h") {
            choice("win", win)
            choice("back", goBack(-1))
          }
        slide(id = 1, title = "Town", content = "t", root = true) {
          choice("hut", hut)
          choice("hut again", hut.copyOf("Hut again"))
        }
      }

    private val dotAll = setOf(RegexOption.DOT_MATCHES_ALL)

    private fun String.decoded() =
      replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&amp;", "&")

    private fun String.cellText() = replace(Regex("<[^>]+>"), "").replace(Regex("\\s+"), " ").trim().decoded()

    // The text of each table row's cells, header rows included; rows without cells are dropped
    fun String.tableRows(): List<List<String>> =
      Regex("<tr[^>]*>(.*?)</tr>", dotAll)
        .findAll(this)
        .map { row ->
          Regex(
            "<t[hd][^>]*>(.*?)</t[hd]>",
            dotAll,
          ).findAll(row.groupValues[1]).map { it.groupValues[1].cellText() }.toList()
        }.filter { it.isNotEmpty() }
        .toList()

    // Each "User: <email>" heading on the reasons page, with the rows of the table that follows it
    fun String.reasonsByUser(): List<Pair<String, List<List<String>>>> =
      split(Regex("<p>\\s*<b>\\s*User:"))
        .drop(1)
        .map { section -> section.substringBefore("</p>").cellText() to section.tableRows() }

    // Each link's target and text, in page order
    fun String.links(): List<Pair<String, String>> =
      Regex("""<a href="([^"]+)">(.*?)</a>""", dotAll)
        .findAll(this)
        .map { it.groupValues[1] to it.groupValues[2].cellText() }
        .toList()
  }
}
