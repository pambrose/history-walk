package com.github.pambrose

import com.github.pambrose.Auth.AUTH_COOKIE
import io.ktor.application.ApplicationCall
import io.ktor.auth.Principal
import io.ktor.sessions.Sessions
import io.ktor.sessions.cookie
import io.ktor.sessions.get
import io.ktor.sessions.sessions
import mu.KLogging
import java.time.Instant
import java.util.*
import kotlin.collections.set
import kotlin.time.Duration

object Cookies : KLogging() {
  fun Sessions.Configuration.assignCookies() {
    cookie<UserId>(AUTH_COOKIE) {
      cookie.path = "/"
      cookie.extensions["SameSite"] = "strict"
      cookie.maxAgeInSeconds = Duration.days(14).inWholeSeconds
    }

//    cookie<BrowserSession>("history_session_id") {
//      cookie.path = "/"
//      cookie.httpOnly = true
//
//      // CSRF protection in modern browsers. Make sure your important side-effect-y operations, like ordering,
//      // uploads, and changing settings, use "unsafe" HTTP verbs like POST and PUT, not GET or HEAD.
//      // https://developer.mozilla.org/en-US/docs/Web/HTTP/Cookies#SameSite_cookies
//      cookie.extensions["SameSite"] = "lax"
//    }

//    cookie<UserPrincipal>(AUTH_COOKIE) {
//      cookie.path = "/" //CHALLENGE_ROOT + "/"
//      cookie.httpOnly = true
//      //if (production)
//      //  cookie.secure = true
//      cookie.maxAgeInSeconds = Duration.days(14).inWholeSeconds
//
//      // CSRF protection in modern browsers. Make sure your important side-effect-y operations, like ordering,
//      // uploads, and changing settings, use "unsafe" HTTP verbs like POST and PUT, not GET or HEAD.
//      // https://developer.mozilla.org/en-US/docs/Web/HTTP/Cookies#SameSite_cookies
//      cookie.extensions["SameSite"] = "lax"
//    }
  }
}

data class BrowserSession(val id: String, val created: Long = Instant.now().toEpochMilli()) {

//  fun sessionDbmsId() =
//    try {
//      querySessionDbmsId(id)
//    } catch (e: MissingBrowserSessionException) {
//      logger.info { "Creating BrowserSession in sessionDbmsId() - ${e.message}" }
//      createBrowserSession(id)
//    }

//  fun answerHistory(md5: String, invocation: Invocation) =
//    SessionAnswerHistoryTable
//      .slice(
//        SessionAnswerHistoryTable.invocation,
//        SessionAnswerHistoryTable.correct,
//        SessionAnswerHistoryTable.incorrectAttempts,
//        SessionAnswerHistoryTable.historyJson
//      )
//      .select { (SessionAnswerHistoryTable.sessionRef eq sessionDbmsId()) and (SessionAnswerHistoryTable.md5 eq md5) }
//      .map {
//        val json = it[SessionAnswerHistoryTable.historyJson]
//        val history = Json.decodeFromString<List<String>>(json).toMutableList()
//        ChallengeHistory(
//          Invocation(it[SessionAnswerHistoryTable.invocation]),
//          it[SessionAnswerHistoryTable.correct],
//          it[SessionAnswerHistoryTable.incorrectAttempts].toInt(),
//          history
//        )
//      }
//      .firstOrNull() ?: ChallengeHistory(invocation)

//  companion object : KLogging() {
//    fun createBrowserSession(id: String) =
//      BrowserSessionsTable
//        .insertAndGetId { row ->
//          row[sessionId] = id
//        }.value
//
//    fun findSessionDbmsId(id: String, createIfMissing: Boolean) =
//      try {
//        querySessionDbmsId(id)
//      } catch (e: MissingBrowserSessionException) {
//        if (createIfMissing) {
//          logger.info { "Creating BrowserSession in findSessionDbmsId() - ${e.message}" }
//          createBrowserSession(id)
//        } else {
//          -1
//        }
//      }
//
//    fun querySessionDbmsId(id: String) =
//      transaction {
//        BrowserSessionsTable
//          .slice(BrowserSessionsTable.id)
//          .select { BrowserSessionsTable.sessionId eq id }
//          .map { it[BrowserSessionsTable.id].value }
//          .firstOrNull() ?: throw MissingBrowserSessionException(id)
//      }
//  }
}

data class UserPrincipal(val uuid: UUID, val created: Long = Instant.now().toEpochMilli()) : Principal

val ApplicationCall.userId get() = sessions.get<UserId>() ?: error("Missing UserId")
//val ApplicationCall.browserSession get() = sessions.get<BrowserSession>()
