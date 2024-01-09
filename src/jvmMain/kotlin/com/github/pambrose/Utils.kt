package com.github.pambrose

import io.ktor.server.application.*
import io.ktor.server.sessions.*
import io.ktor.util.pipeline.*
import org.jetbrains.exposed.dao.id.EntityID
import java.util.*

object Utils {
  private const val LT_ESCAPE = "---LT---"
  private const val GT_ESCAPE = "---GT---"
  private const val DQUOTE_ESCAPE = "---DQ---"
  private const val SQUOTE_ESCAPE = "---SQ---"

  fun String.transformText(): String {
    val escaped = this
      .replace("<", LT_ESCAPE)
      .replace(">", GT_ESCAPE)
      .replace("\"", DQUOTE_ESCAPE)
      .replace("\"", SQUOTE_ESCAPE)

    return MarkdownParser.toHtml(escaped)
      .replace(LT_ESCAPE, "<")
      .replace(GT_ESCAPE, ">")
      .replace(DQUOTE_ESCAPE, "'")
      .replace(SQUOTE_ESCAPE, "\"")
  }

  val ApplicationCall.userPrincipal get() = sessions.get<UserPrincipal>()

  fun String.toUuid(): UUID = UUID.fromString(this)

  fun <T : Comparable<T>> EntityID<T>.toUuid(): UUID = this.toString().toUuid()

//  fun <T> readonlyTx(db: Database? = null, statement: Transaction.() -> T): T =
//    transaction(
//      db.transactionManager.defaultIsolationLevel,
//      db.transactionManager.defaultRepetitionAttempts,
//      true,
//      db,
//      statement
//    )
}

typealias PipelineCall = PipelineContext<Unit, ApplicationCall>
