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

/** Adds or updates the student `<localPart>@athenian.org`, as a sign-in does, and returns their id as a string. */
fun student(
  localPart: String,
  name: String = "Student $localPart",
) = upsertFromGoogle("$localPart@athenian.org", name, null, Role.STUDENT).toString()

/**
 * Adds or updates the teacher `<localPart>@athenian.org`, as a sign-in does, and returns their id as a string.
 * The stored role is what the progress views filter on; a route still needs the address among its teacher emails.
 */
fun teacher(localPart: String) = upsertFromGoogle("$localPart@athenian.org", "Teacher", null, Role.TEACHER).toString()

/** A time on a fixed day, [minute] minutes past nine, for a test that sets the order of its moves. */
fun time(minute: Int) = LocalDateTime(2026, 10, 31, 9, minute)

/**
 * Stores a move from [from] to [to] straight in the table, past the service's checks. Its titles are the paths' last
 * parts and its choice text `choose <title>`; its time is [at], or the database's now when [at] is null.
 */
fun addChoice(
  user: String,
  from: String,
  to: String,
  deadEnd: Boolean = false,
  reason: String = "because",
  at: LocalDateTime? = null,
  countedAsStudent: Boolean = false,
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
    row[this.countedAsStudent] = countedAsStudent
  }
}
