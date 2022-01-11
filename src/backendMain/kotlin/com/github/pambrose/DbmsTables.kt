package com.github.pambrose

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.jodatime.datetime

object UsersTable : UUIDTable("history.users") {
  val created = datetime("created")
  val updated = datetime("updated")
  val email = text("email")
  val fullName = text("full_name")
  val salt = text("salt")
  val digest = text("digest")
  val lastPathName = text("last_path_name")
}

object UserChoiceTable : IntIdTable("history.userchoices") {
  val created = datetime("created")
  val updated = datetime("updated")
  val userUuidRef = uuid("user_uuid_ref")
  val fromPathName = text("from_path_name")
  val fromTitle = text("from_title")
  val toPathName = text("to_path_name")
  val toTitle = text("to_title")
  val choiceText = text("choice_text")
  val reason = text("reason")
}

object UserVisitsView : Table("history.user_visits") {
  val id = uuid("id")
  val toTitle = text("to_title")
}

object UserDecisionCountsView : Table("history.user_decision_counts") {
  val id = uuid("id")
  val fullName = text("full_name")
  val email = text("email")
  val lastPathName = text("last_path_name")
  val decisionCount = integer("decision_count")
}