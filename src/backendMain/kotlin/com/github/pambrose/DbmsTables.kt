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
  val lastfqName = text("last_title")
}

object UserChoiceTable : LongIdTable("history.userchoices") {
  val created = datetime("created")
  val updated = datetime("updated")
  val uuidCol = uuid("uuid")
  val userUuid = uuid("user_uuid")
  val fromfqName = text("from_fqname")
  val fromTitle = text("from_title")
  val tofqName = text("to_fqname")
  val toTitle = text("to_title")
  val choiceText = text("choice_text")
  val reason = text("reason")
}