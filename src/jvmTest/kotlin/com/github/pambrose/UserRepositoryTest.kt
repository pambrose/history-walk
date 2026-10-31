package com.github.pambrose

import com.github.pambrose.User.Companion.emailForUuid
import com.github.pambrose.User.Companion.upsertFromGoogle
import com.github.pambrose.User.Companion.userInfoFor
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import java.sql.SQLException
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
  private val legacyId = UUID.randomUUID()

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
      // A V001-era row with a mixed-case email exists before V002 is applied.
      Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("001").load().migrate()
      ds.connection.use { c ->
        c.prepareStatement(
          "insert into history.users (id, email, full_name, salt, digest, last_path_name) values (?, ?, 'Legacy', 's', 'd', 'root')",
        ).use { ps ->
          ps.setObject(1, legacyId)
          ps.setString(2, "Mixed.Case@Athenian.org")
          ps.executeUpdate()
        }
      }
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
      val info = userInfoFor(a, setOf("pambrose@athenian.org"))!!
      info.fullName shouldBe "Paul Ambrose"
      info.pictureUrl shouldBe "https://lh3.googleusercontent.com/a/p"
      info.role shouldBe Role.TEACHER
      transaction {
        UsersTable.selectAll().where { UsersTable.id eq a }.single()[UsersTable.lastSignIn].shouldNotBeNull()
      }
    }

    "V002 lowercases existing emails so a returning user keeps the same row" {
      emailForUuid(legacyId) shouldBe "mixed.case@athenian.org"
      upsertFromGoogle("mixed.case@athenian.org", "Legacy", null, Role.STUDENT) shouldBe legacyId
    }

    "the role comes from the current teacher list, not the stored one" {
      val id = upsertFromGoogle("27promoted@athenian.org", "Promoted", null, Role.STUDENT)
      userInfoFor(id, emptySet())!!.role shouldBe Role.STUDENT
      userInfoFor(id, setOf("27promoted@athenian.org"))!!.role shouldBe Role.TEACHER
    }

    "a mixed-case email cannot be inserted directly" {
      shouldThrow<SQLException> {
        ds.connection.use { c ->
          c.prepareStatement(
            "insert into history.users (id, email, full_name, last_path_name) values (?, 'Mixed@Athenian.org', 'X', 'root')",
          ).use { ps ->
            ps.setObject(1, UUID.randomUUID())
            ps.executeUpdate()
          }
        }
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
