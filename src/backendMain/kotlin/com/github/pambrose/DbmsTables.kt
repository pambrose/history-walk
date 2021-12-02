package com.github.pambrose

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.jodatime.datetime

object UsersTable : LongIdTable("history.users") {
  val created = datetime("created")
  val updated = datetime("updated")
  val uuidCol = uuid("uuid")
  val email = text("email")
  val fullName = text("full_name")
  val salt = text("salt")
  val digest = text("digest")
  val lastPathName = text("last_path_name")
}

object UserChoiceTable : LongIdTable("history.userchoices") {
  val created = datetime("created")
  val updated = datetime("updated")
  val uuidCol = uuid("uuid")
  val userUuid = uuid("user_uuid")
  val fromPathName = text("from_path_name")
  val fromTitle = text("from_title")
  val toPathName = text("to_path_name")
  val toTitle = text("to_title")
  val choiceText = text("choice_text")
  val reason = text("reason")
}