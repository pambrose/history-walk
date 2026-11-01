package com.pambrose.historywalk.dbms

import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.datetime.datetime

object UsersTable : UUIDTable("history.users") {
  val created = datetime("created")
  val updated = datetime("updated")
  val email = text("email")
  val fullName = text("full_name")
  val pictureUrl = text("picture_url").nullable()
  val role = text("role")
  val lastSignIn = datetime("last_sign_in").nullable()
  val lastPathName = text("last_path_name")
}

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
}

object UserVisitsView : Table("history.user_visits") {
  val id = javaUUID("id")
  val toTitle = text("to_title")
}

object UserDecisionCountsView : Table("history.user_decision_counts") {
  val id = javaUUID("id")
  val fullName = text("full_name")
  val email = text("email")
  val lastPathName = text("last_path_name")
  val decisionCount = integer("decision_count")
}

object UserReasonsView : Table("history.user_reasons") {
  val id = javaUUID("id")
  val fullName = text("full_name")
  val email = text("email")
  val fromPathName = text("from_path_name")
  val fromTitle = text("from_title")
  val toPathName = text("to_path_name")
  val toTitle = text("to_title")
  val reason = text("reason")
}
