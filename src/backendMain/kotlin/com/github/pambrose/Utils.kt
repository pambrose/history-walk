package com.github.pambrose

import io.ktor.server.application.*
import io.ktor.server.sessions.*
import io.ktor.util.pipeline.*
import org.jetbrains.exposed.dao.id.EntityID
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
}

typealias PipelineCall = PipelineContext<Unit, ApplicationCall>