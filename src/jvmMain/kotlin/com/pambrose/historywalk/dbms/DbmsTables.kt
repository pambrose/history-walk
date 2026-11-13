package com.pambrose.historywalk.dbms

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.datetime.datetime

/**
 * The `history.users` table: one row for each person who has signed in, by a lowercase address, with their stored
 * role and the slide they stand on ([lastPathName], a path name).
 */
object UsersTable : UUIDTable("history.users") {
  val created = datetime("created")
  val updated = datetime("updated")
  val email = text("email")
  val fullName = text("full_name")
  val pictureUrl = text("picture_url").nullable()
  val role = text("role")
  val lastSignIn = datetime("last_sign_in").nullable()
  val lastPathName = text("last_path_name")

  // Signing out increments it, ending every session cookie that carries the old value (V002)
  val sessionVersion = integer("session_version").default(0)
}

/**
 * The `history.user_choices` table: one row for each move a user made and gave a reason for, by path names, with the
 * titles and choice text as they were. One row per user and move (`user_choices_unique`), so the first reason stays.
 */
object UserChoiceTable : IntIdTable("history.user_choices") {
  val created = datetime("created")
  val updated = datetime("updated")
  val userUuidRef = javaUUID("user_uuid_ref")
  val fromPathName = text("from_path_name")
  val fromTitle = text("from_title")
  val toPathName = text("to_path_name")
  val toTitle = text("to_title")
  val deadEnd = bool("dead_end")
  val choiceText = text("choice_text")
  val reason = text("reason")

  // A teacher's move made while teachers saw the student view, which the progress views count as a student's (V003)
  val countedAsStudent = bool("counted_as_student").default(false)
}

/** The `history.settings` table: the app's settings, one row ([ROW]) (V003). */
object SettingsTable : Table("history.settings") {
  val id = integer("id")
  val teachersSeeStudentView = bool("teachers_see_student_view")
  val updated = datetime("updated")

  override val primaryKey = PrimaryKey(id)

  /** The id of the one row. */
  const val ROW = 1
}

/**
 * The `history.user_visits` view: the slide each counted move reached, by title, with the mover's id. A counted move
 * is a student's, or a teacher's made in the student view.
 */
object UserVisitsView : Table("history.user_visits") {
  val id = javaUUID("id")
  val toTitle = text("to_title")
}

/**
 * The `history.user_decision_counts` view: every student, and every teacher with a counted move, with the slide they
 * stand on and their number of counted moves.
 */
object UserDecisionCountsView : Table("history.user_decision_counts") {
  val id = javaUUID("id")
  val fullName = text("full_name")
  val email = text("email")
  val lastPathName = text("last_path_name")
  val decisionCount = integer("decision_count")
}

/**
 * The `history.user_reasons` view: every counted move with the reason given for it and who made it, by address, then
 * in the order made.
 */
object UserReasonsView : Table("history.user_reasons") {
  val id = javaUUID("id")
  val fullName = text("full_name")
  val email = text("email")
  val fromPathName = text("from_path_name")
  val fromTitle = text("from_title")
  val toPathName = text("to_path_name")
  val toTitle = text("to_title")
  val reason = text("reason")

  // The choice the reason explains, as its button read when the student made it (V006)
  val choiceText = text("choice_text")
}
