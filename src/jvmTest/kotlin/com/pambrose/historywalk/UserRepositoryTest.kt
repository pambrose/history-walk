package com.pambrose.historywalk

import com.pambrose.historywalk.User.Companion.emailForUuid
import com.pambrose.historywalk.User.Companion.upsertFromGoogle
import com.pambrose.historywalk.User.Companion.userInfoFor
import com.pambrose.historywalk.dbms.UserChoiceTable
import com.pambrose.historywalk.dbms.UserDecisionCountsView
import com.pambrose.historywalk.dbms.UserReasonsView
import com.pambrose.historywalk.dbms.UserVisitsView
import com.pambrose.historywalk.dbms.UsersTable
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.sql.SQLException
import java.util.*

class UserRepositoryTest : StringSpec() {
  private lateinit var db: TestPostgres

  private fun addChoice(userId: UUID) =
    transaction {
      UserChoiceTable.insert { row ->
        row[userUuidRef] = userId
        row[fromPathName] = "a"
        row[fromTitle] = "A"
        row[toPathName] = "b"
        row[toTitle] = "B"
        row[deadEnd] = false
        row[choiceText] = "go"
        row[reason] = "because"
      }
    }

  init {
    beforeSpec { db = TestPostgres() }
    afterSpec { db.close() }

    "the schema migration is applied" {
      val rows = mutableListOf<Pair<String, Boolean>>()
      db.dataSource.connection.use { c ->
        c.createStatement().use { st ->
          st
            .executeQuery("select version, success from public.flyway_schema_history order by installed_rank")
            .use { rs -> while (rs.next()) rows += rs.getString(1) to rs.getBoolean(2) }
        }
      }
      rows.map { it.first }.shouldContainExactly("001")
      rows.all { it.second } shouldBe true
    }

    "first sign-in creates the user" {
      val id = upsertFromGoogle("27msmith@athenian.org", "Maya Smith", null, Role.STUDENT)
      userInfoFor(id) shouldBe UserInfo("Maya Smith", "27msmith@athenian.org", null, Role.STUDENT)
    }

    "a later sign-in updates the same row, whatever the case" {
      val a = upsertFromGoogle("pambrose@athenian.org", "Paul", null, Role.TEACHER)
      val b =
        upsertFromGoogle("PAmbrose@Athenian.org", "Paul Ambrose", "https://lh3.googleusercontent.com/a/p", Role.TEACHER)
      b shouldBe a
      val info = userInfoFor(a, setOf("pambrose@athenian.org"))!!
      info.fullName shouldBe "Paul Ambrose"
      info.pictureUrl shouldBe "https://lh3.googleusercontent.com/a/p"
      info.role shouldBe Role.TEACHER
      transaction {
        UsersTable
          .selectAll()
          .where { UsersTable.id eq a }
          .single()[UsersTable.lastSignIn]
          .shouldNotBeNull()
      }
    }

    "the role comes from the current teacher list, not the stored one" {
      val id = upsertFromGoogle("27promoted@athenian.org", "Promoted", null, Role.STUDENT)
      userInfoFor(id, emptySet())!!.role shouldBe Role.STUDENT
      userInfoFor(id, setOf("27promoted@athenian.org"))!!.role shouldBe Role.TEACHER
    }

    "a mixed-case email cannot be inserted directly" {
      fun insertUser(email: String) =
        db.dataSource.connection.use { c ->
          c
            .prepareStatement(
            "insert into history.users (id, email, full_name, role, last_path_name) values (?, ?, 'X', 'student', 'root')",
          ).use { ps ->
            ps.setObject(1, UUID.randomUUID())
            ps.setString(2, email)
            ps.executeUpdate()
          }
        }

      // The same insert with a lowercase address succeeds, so the failure below is the lowercase check itself.
      insertUser("27lower@athenian.org") shouldBe 1
      val e = shouldThrow<SQLException> { insertUser("27Mixed@Athenian.org") }
      e.sqlState shouldBe "23514" // check_violation
      e.message.orEmpty() shouldContain "users_email_lowercase"
    }

    "emailForUuid finds the address, or null" {
      val id = upsertFromGoogle("27msmith@athenian.org", "Maya Smith", null, Role.STUDENT)
      emailForUuid(id) shouldBe "27msmith@athenian.org"
      emailForUuid(UUID.randomUUID()) shouldBe null
    }

    "progress views leave teachers out" {
      val student = upsertFromGoogle("27views@athenian.org", "Student", null, Role.STUDENT)
      val teacher = upsertFromGoogle("teacherviews@athenian.org", "Teacher", null, Role.TEACHER)
      addChoice(student)
      addChoice(teacher)
      transaction {
        UserDecisionCountsView
          .selectAll()
          .map { it[UserDecisionCountsView.id] }
          .distinct()
          .also { ids ->
            (student in ids) shouldBe true
            (teacher in ids) shouldBe false
          }
        UserReasonsView
          .selectAll()
          .map { it[UserReasonsView.id] }
          .distinct()
          .also { ids -> ids shouldContainExactly listOf(student) }
        UserVisitsView
          .selectAll()
          .map { it[UserVisitsView.id] }
          .distinct()
          .also { ids -> ids shouldContainExactly listOf(student) }
      }
    }
  }
}
