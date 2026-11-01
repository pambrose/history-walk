package com.pambrose.historywalk

import com.pambrose.historywalk.User.Companion.upsertFromGoogle
import com.pambrose.historywalk.dbms.UserChoiceTable
import com.pambrose.historywalk.slides.SlideDeck.Companion.slideDeck
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

// A small deck and helpers shared by the specs that run against TestPostgres

const val START = "/Start"
const val MIDDLE = "/Start/Middle"
const val WIN = "/Start/Middle/Win"
const val LOSE = "/Start/Lose"

// /Start -> /Start/Middle (horizontal) -> /Start/Middle/Win, or /Start/Lose, a dead end
val testDeck =
  slideDeck {
    val win = slide(id = 3, title = "Win", content = "You **won**", success = true)
    val lose = slide(id = 2, title = "Lose", content = "Wrong way")
    val middle =
      slide(id = 4, title = "Middle", content = "Keep going") {
        horizontalChoices()
        choice("finish", win)
        choice("start over", goBack(-1))
      }
    slide(id = 1, title = "Start", content = "Begin", root = true) {
      choice("go on", middle)
      choice("go wrong", lose)
    }
  }

fun student(
  localPart: String,
  name: String = "Student $localPart",
) = upsertFromGoogle("$localPart@athenian.org", name, null, Role.STUDENT).toString()

fun teacher(localPart: String) = upsertFromGoogle("$localPart@athenian.org", "Teacher", null, Role.TEACHER).toString()

fun time(minute: Int) = LocalDateTime(2026, 10, 31, 9, minute)

fun addChoice(
  user: String,
  from: String,
  to: String,
  deadEnd: Boolean = false,
  reason: String = "because",
  at: LocalDateTime? = null,
) = transaction {
  UserChoiceTable.insert { row ->
    row[userUuidRef] = UUID.fromString(user)
    row[fromPathName] = from
    row[fromTitle] = from.substringAfterLast('/')
    row[toPathName] = to
    row[toTitle] = to.substringAfterLast('/')
    row[this.deadEnd] = deadEnd
    row[choiceText] = "choose ${to.substringAfterLast('/')}"
    row[this.reason] = reason
    if (at != null) row[created] = at
  }
}
