package com.pambrose.historywalk.auth

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLParameter
import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.sessions.defaultSessionSerializer
import io.ktor.server.testing.testApplication
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

class UserSessionTest : StringSpec() {
  private val student = UUID.randomUUID()
  private val teacher = UUID.randomUUID()
  private val emails = mapOf(student to "27msmith@athenian.org", teacher to "pambrose@athenian.org")
  private val teachers = setOf("pambrose@athenian.org")

  // Whole milliseconds, as the cookie stores the sign-in time, so a session made at `now - LIFETIME` is exactly that old
  private val now: Instant = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds())

  // Each user at session version 0 unless a test moves it on
  private val versions = mutableMapOf<UUID, Int>()

  private fun findUser(uuid: UUID) = emails[uuid]?.let { SessionUser(it, versions[uuid] ?: 0) }

  private fun session(
    uuid: UUID,
    createdAt: Instant = now,
    version: Int = 0,
  ) = UserSession(uuid.toString(), createdAt.toEpochMilliseconds(), version)

  private fun Application.module(secure: Boolean = false) {
    installUserSessions(secure = secure, findUser = ::findUser, teacherEmails = { teachers })
    routing {
      get("/set/{id}") {
        call.assignUserSession(UUID.fromString(call.parameters["id"]!!))
        call.respondText("ok")
      }
      get("/who") { call.respondText(call.signedInUserUuid() ?: "none") }
    }
  }

  // The cookie value the server would write for [payload], a session serialized to JSON
  private fun encryptedCookie(payload: String) =
    SessionConfig.encryptTransformer().transformWrite(payload).encodeURLParameter()

  init {
    beforeTest { versions.clear() }

    "a student's fresh session is trusted" {
      untrustedBecause(session(student), now, ::findUser, teachers) shouldBe null
    }
    "a listed teacher's session is trusted" {
      untrustedBecause(session(teacher), now, ::findUser, teachers) shouldBe null
    }
    "a teacher removed from the list is signed out" {
      untrustedBecause(session(teacher), now, ::findUser, emptySet()).shouldNotBeNull()
    }
    "a deleted user is signed out" {
      untrustedBecause(session(UUID.randomUUID()), now, ::findUser, teachers).shouldNotBeNull()
    }
    "a session just inside 12 hours is trusted" {
      untrustedBecause(session(student, createdAt = now - 12.hours), now, ::findUser, teachers) shouldBe null
    }
    "a session past 12 hours is signed out" {
      untrustedBecause(
        session(student, createdAt = now - 12.hours - 1.milliseconds),
        now,
        ::findUser,
        teachers,
      ).shouldNotBeNull() shouldContain "signed in more than"
    }
    "a non-UUID id is signed out" {
      untrustedBecause(UserSession("not-a-uuid", now.toEpochMilliseconds()), now, ::findUser, teachers)
        .shouldNotBeNull()
    }
    "a session carrying the stored version is trusted" {
      versions[student] = 3
      untrustedBecause(session(student, version = 3), now, ::findUser, teachers) shouldBe null
    }
    "a session from before the user signed out is signed out" {
      versions[student] = 1
      untrustedBecause(session(student, version = 0), now, ::findUser, teachers)
        .shouldNotBeNull() shouldContain "signed out since"
    }
    "a session ahead of the stored version is signed out too" {
      untrustedBecause(session(student, version = 1), now, ::findUser, teachers).shouldNotBeNull()
    }

    "a cookie payload without a version reads as version 0" {
      val serializer = defaultSessionSerializer<UserSession>()
      serializer.deserialize("""{"uuid":"$student","created":123}""") shouldBe UserSession(student.toString(), 123, 0)
    }
    "a non-zero version round-trips through the cookie payload" {
      val serializer = defaultSessionSerializer<UserSession>()
      val session = session(student, version = 7)
      serializer.serialize(session) shouldContain "\"sessionVersion\":7"
      serializer.deserialize(serializer.serialize(session)) shouldBe session
    }
    "version 0 is left out of the cookie payload, as a cookie from before versions has none" {
      val serializer = defaultSessionSerializer<UserSession>()
      val session = session(student)
      serializer.serialize(session) shouldNotContain "sessionVersion"
      serializer.deserialize(serializer.serialize(session)) shouldBe session
    }

    "the cookie round-trips" {
      testApplication {
        application { module() }
        val client = createClient { install(HttpCookies) }
        client.get("/set/$student")
        client.get("/who").bodyAsText() shouldBe student.toString()
      }
    }
    "sign-in writes the user's stored version, so the session is trusted" {
      versions[student] = 2
      testApplication {
        application { module() }
        val client = createClient { install(HttpCookies) }
        client.get("/set/$student")
        client.get("/who").bodyAsText() shouldBe student.toString()
      }
    }
    "a cookie written before versions is trusted until the user signs out" {
      val cookie = encryptedCookie("""{"uuid":"$student","created":${now.toEpochMilliseconds()}}""")
      testApplication {
        application { module() }
        client.get("/who") { header(HttpHeaders.Cookie, "HistoryWalkAuth=$cookie") }.bodyAsText() shouldBe
          student.toString()
        versions[student] = 1
        val response = client.get("/who") { header(HttpHeaders.Cookie, "HistoryWalkAuth=$cookie") }
        response.bodyAsText() shouldBe "none"
        response.headers[HttpHeaders.SetCookie].shouldNotBeNull() shouldStartWith "HistoryWalkAuth=;"
      }
    }
    "a tampered cookie is ignored" {
      testApplication {
        application { module() }
        val value = client
          .get("/set/$student")
          .headers[HttpHeaders.SetCookie]!!
          .substringAfter("HistoryWalkAuth=")
          .substringBefore(";")
        val i = value.length / 2
        val tampered = value.substring(0, i) + (if (value[i] == 'a') 'b' else 'a') + value.substring(i + 1)
        val response = client.get("/who") { header(HttpHeaders.Cookie, "HistoryWalkAuth=$tampered") }
        response.bodyAsText() shouldBe "none"
      }
    }
    "a password-era cookie is ignored" {
      testApplication {
        application { module() }
        val response = client.get("/who") { header(HttpHeaders.Cookie, "HistoryWalkAuth=uuid%3D%23s$student") }
        response.bodyAsText() shouldBe "none"
        response.status shouldBe HttpStatusCode.OK
      }
    }
    "a deleted user's cookie is cleared" {
      testApplication {
        application { module() }
        val client = createClient { install(HttpCookies) }
        client.get("/set/${UUID.randomUUID()}")
        client.get("/who").bodyAsText() shouldBe "none"
      }
    }
    "production cookies carry the host prefix and flags, and last 12 hours" {
      testApplication {
        application { module(secure = true) }
        val header = client.get("/set/$student").headers[HttpHeaders.SetCookie]!!
        header shouldStartWith "__Host-HistoryWalkAuth="
        header shouldContain "Secure"
        header shouldContain "HttpOnly"
        header shouldContain "SameSite=strict"
        header shouldContain "Max-Age=43200"
      }
    }
  }
}
