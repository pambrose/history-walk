package com.github.pambrose

import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.sessions.*
import java.time.Instant
import java.util.*

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

internal data class UserPrincipal(val uuid: UUID, val created: Long = Instant.now().toEpochMilli()) : Principal

internal val ApplicationCall.browserSession get() = sessions.get<BrowserSession>()

internal val ApplicationCall.userPrincipal get() = sessions.get<UserPrincipal>()
