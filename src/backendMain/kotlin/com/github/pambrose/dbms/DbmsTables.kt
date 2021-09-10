package com.github.pambrose.dbms

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.jodatime.datetime

object UsersTable : LongIdTable("users") {
  val created = datetime("created")
  val updated = datetime("updated")
  val uuidCol = uuid("uuid")
  val userId = text("user_id")
  val email = text("email")
  val fullName = text("name")
  val salt = text("salt")
  val digest = text("digest")
}
