package com.pambrose.historywalk.auth

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

class UserSessionTest : StringSpec() {
  private val student = UUID.randomUUID()
  private val teacher = UUID.randomUUID()
  private val emails = mapOf(student to "27msmith@athenian.org", teacher to "pambrose@athenian.org")
  private val teachers = setOf("pambrose@athenian.org")
  private val now: Instant = Clock.System.now()

  private fun session(
    uuid: UUID,
    createdAt: Instant = now,
  ) = UserSession(uuid.toString(), createdAt.toEpochMilliseconds())

  private fun Application.module(secure: Boolean = false) {
    installUserSessions(secure = secure, findEmail = emails::get, teacherEmails = { teachers })
    routing {
      get("/set/{id}") {
        call.assignUserSession(UUID.fromString(call.parameters["id"]!!))
        call.respondText("ok")
      }
      get("/who") { call.respondText(call.signedInUserUuid() ?: "none") }
    }
  }

  init {
    "a student's fresh session is trusted" {
      untrustedBecause(session(student), now, emails::get, teachers) shouldBe null
    }
    "a listed teacher's session is trusted" {
      untrustedBecause(session(teacher), now, emails::get, teachers) shouldBe null
    }
    "a teacher removed from the list is signed out" {
      untrustedBecause(session(teacher), now, emails::get, emptySet()).shouldNotBeNull()
    }
    "a deleted user is signed out" {
      untrustedBecause(session(UUID.randomUUID()), now, emails::get, teachers).shouldNotBeNull()
    }
    "a session past 14 days is signed out" {
      untrustedBecause(
        session(student, createdAt = now - 14.days - 1.milliseconds),
        now,
        emails::get,
        teachers,
      ).shouldNotBeNull()
    }
    "a non-UUID id is signed out" {
      untrustedBecause(UserSession("not-a-uuid", now.toEpochMilliseconds()), now, emails::get, teachers)
        .shouldNotBeNull()
    }

    "the cookie round-trips" {
      testApplication {
        application { module() }
        val client = createClient { install(HttpCookies) }
        client.get("/set/$student")
        client.get("/who").bodyAsText() shouldBe student.toString()
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
    "production cookies carry the host prefix and flags" {
      testApplication {
        application { module(secure = true) }
        val header = client.get("/set/$student").headers[HttpHeaders.SetCookie]!!
        header shouldStartWith "__Host-HistoryWalkAuth="
        header shouldContain "Secure"
        header shouldContain "HttpOnly"
        header shouldContain "SameSite=strict"
        header shouldContain "Max-Age=1209600"
      }
    }
  }
}
