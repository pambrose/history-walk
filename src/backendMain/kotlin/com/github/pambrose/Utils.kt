package com.github.pambrose

import io.ktor.server.application.*
import io.ktor.server.sessions.*
import io.ktor.util.pipeline.*
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.transactions.transactionManager
import java.util.*

object Utils {
  private const val ltEscape = "---LT---"
  private const val gtEscape = "---GT---"
  private const val dquoteEscape = "---DQ---"
  private const val squoteEscape = "---SQ---"

  fun String.transformText(): String {
    val escaped = this
      .replace("<", ltEscape)
      .replace(">", gtEscape)
      .replace("\"", dquoteEscape)
      .replace("\"", squoteEscape)

    return MarkdownParser.toHtml(escaped)
      .replace(ltEscape, "<")
      .replace(gtEscape, ">")
      .replace(dquoteEscape, "'")
      .replace(squoteEscape, "\"")
  }

  val ApplicationCall.userPrincipal get() = sessions.get<UserPrincipal>()

  fun String.toUuid(): UUID = UUID.fromString(this)

  fun <T : Comparable<T>> EntityID<T>.toUuid(): UUID = this.toString().toUuid()

  fun <T> readonlyTx(db: Database? = null, statement: Transaction.() -> T): T =
    transaction(
      db.transactionManager.defaultIsolationLevel,
      db.transactionManager.defaultRepetitionAttempts,
      true,
      db,
      statement
    )

}

typealias PipelineCall = PipelineContext<Unit, ApplicationCall>