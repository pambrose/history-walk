package com.github.pambrose

import com.github.pambrose.User.Companion.emailForUuid
import com.github.pambrose.User.Companion.upsertFromGoogle
import com.github.pambrose.User.Companion.userInfoFor
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.testcontainers.postgresql.PostgreSQLContainer
import java.util.*

class UserRepositoryTest : StringSpec() {
  private lateinit var container: PostgreSQLContainer
  private lateinit var ds: HikariDataSource

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
    beforeSpec {
      container = PostgreSQLContainer("postgres:18").also { it.start() }
      ds =
        HikariDataSource(
          HikariConfig().apply {
            jdbcUrl = container.jdbcUrl
            username = container.username
            password = container.password
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 4
          },
        )
      Dbms.migrate(ds)
      Database.connect(ds)
    }

    afterSpec {
      ds.close()
      container.stop()
    }

    "both migrations are applied" {
      val rows = mutableListOf<Pair<String, Boolean>>()
      ds.connection.use { c ->
        c.createStatement().use { st ->
          st.executeQuery("select version, success from public.flyway_schema_history order by installed_rank")
            .use { rs -> while (rs.next()) rows += rs.getString(1) to rs.getBoolean(2) }
        }
      }
      rows.map { it.first }.shouldContainExactly("001", "002")
      rows.all { it.second } shouldBe true
    }

    "first sign-in creates the user" {
      val id = upsertFromGoogle("27msmith@athenian.org", "Maya Smith", null, Role.STUDENT)
      userInfoFor(id) shouldBe UserInfo("Maya Smith", "27msmith@athenian.org", null, Role.STUDENT)
    }

    "a later sign-in updates the same row, whatever the case" {
      val a = upsertFromGoogle("pambrose@athenian.org", "Paul", null, Role.TEACHER)
      val b = upsertFromGoogle("PAmbrose@Athenian.org", "Paul Ambrose", "https://lh3.googleusercontent.com/a/p", Role.TEACHER)
      b shouldBe a
      val info = userInfoFor(a)!!
      info.fullName shouldBe "Paul Ambrose"
      info.pictureUrl shouldBe "https://lh3.googleusercontent.com/a/p"
      info.role shouldBe Role.TEACHER
      transaction {
        UsersTable.selectAll().where { UsersTable.id eq a }.single()[UsersTable.lastSignIn].shouldNotBeNull()
      }
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
        UserDecisionCountsView.selectAll().map { it[UserDecisionCountsView.id] }.distinct()
          .also { ids ->
            (student in ids) shouldBe true
            (teacher in ids) shouldBe false
          }
        UserReasonsView.selectAll().map { it[UserReasonsView.id] }.distinct()
          .also { ids -> ids shouldContainExactly listOf(student) }
        UserVisitsView.selectAll().map { it[UserVisitsView.id] }.distinct()
          .also { ids -> ids shouldContainExactly listOf(student) }
      }
    }
  }
}
