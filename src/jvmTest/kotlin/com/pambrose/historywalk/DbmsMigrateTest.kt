package com.pambrose.historywalk

import com.pambrose.historywalk.dbms.Dbms
import com.zaxxer.hikari.HikariDataSource
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.flywaydb.core.Flyway
import java.sql.ResultSet
import java.util.UUID
import javax.sql.DataSource

/**
 * Dbms.migrate on fresh, migrated, and broken databases. V001 begins with `drop schema if exists history cascade`, and
 * Flyway, left to itself, re-runs it on a database whose history schema survived without Flyway's history table (or
 * with that table emptied), dropping every user and choice; these specs pin that startup refuses instead.
 */
class DbmsMigrateTest : StringSpec() {
  private lateinit var db: TestPostgres
  private var databases = 0

  // Each test gets a database of its own in the shared container, so one that breaks Flyway's history breaks no other
  private fun withDatabase(block: (HikariDataSource) -> Unit) = db.emptyDatabase("migrate_${++databases}").use(block)

  private fun DataSource.exec(sql: String) = connection.use { c -> c.createStatement().use { st -> st.execute(sql) } }

  private fun <T> DataSource.query(
    sql: String,
    row: (ResultSet) -> T,
  ): List<T> =
    connection.use { c ->
      c.createStatement().use { st ->
        st.executeQuery(sql).use { rs -> buildList { while (rs.next()) add(row(rs)) } }
      }
    }

  private fun DataSource.count(table: String) = query("select count(*) from $table") { it.getLong(1) }.single()

  // The users and choices a walk leaves behind, the data a re-run of V001 would drop
  private fun DataSource.walkCounts() = count("history.users") to count("history.user_choices")

  private fun DataSource.addWalk() {
    val user = UUID.randomUUID()
    exec(
      "insert into history.users (id, email, full_name, role, last_path_name) " +
        "values ('$user', '27walker@athenian.org', 'Walker', 'student', '$MIDDLE')",
    )
    exec(
      "insert into history.user_choices " +
        "(user_uuid_ref, from_path_name, from_title, to_path_name, to_title, dead_end, choice_text, reason) " +
        "values ('$user', '$START', 'Start', '$MIDDLE', 'Middle', false, 'go on', 'because it looks right')",
    )
  }

  // Each applied migration as Flyway recorded it; installed_on changes if a migration runs again
  private fun DataSource.flywayHistory() =
    query(
      "select installed_rank, version, checksum, installed_on from public.flyway_schema_history order by installed_rank",
    ) { rs -> (1..4).joinToString(" ") { rs.getString(it).orEmpty() } }

  private fun DataSource.flywayTableExists() =
    query("select to_regclass('public.flyway_schema_history') is not null") { it.getBoolean(1) }.single()

  private fun flyway(ds: DataSource) = Flyway.configure().dataSource(ds).locations("classpath:db/migration")

  // The newest migration's version, the one a fully migrated database is at
  private fun latestMigration(ds: DataSource) =
    flyway(ds)
      .load()
      .info()
      .all()
      .mapNotNull { it.version }
      .max()
      .toString()

  init {
    beforeSpec { db = TestPostgres() }
    afterSpec { db.close() }

    "a fresh database migrates" {
      withDatabase { ds ->
        val result = Dbms.migrate(ds)
        result.migrationsExecuted shouldBeGreaterThan 0
        ds.flywayHistory().size shouldBe result.migrationsExecuted
        ds.walkCounts() shouldBe (0L to 0L)
      }
    }

    "a history schema with no tables does not stop the migration" {
      withDatabase { ds ->
        ds.exec("create schema history")
        Dbms.migrate(ds).migrationsExecuted shouldBeGreaterThan 0
        ds.walkCounts() shouldBe (0L to 0L)
      }
    }

    "a migrated database starts again with no migration run and no data lost" {
      withDatabase { ds ->
        Dbms.migrate(ds)
        ds.addWalk()
        val history = ds.flywayHistory()

        Dbms.migrate(ds).migrationsExecuted shouldBe 0
        ds.flywayHistory() shouldBe history
        ds.walkCounts() shouldBe (1L to 1L)
      }
    }

    "a database whose Flyway history table was dropped is refused, its data untouched" {
      withDatabase { ds ->
        Dbms.migrate(ds)
        ds.addWalk()
        ds.exec("drop table public.flyway_schema_history")

        val e = shouldThrow<IllegalStateException> { Dbms.migrate(ds) }
        e.message.orEmpty() shouldContain "Flyway's schema history table public.flyway_schema_history is missing"
        e.message.orEmpty() shouldContain "Flyway would run V001"
        ds.walkCounts() shouldBe (1L to 1L)
        // Refused before Flyway created a new, empty table, so restoring the old one has nothing in its way
        ds.flywayTableExists() shouldBe false
      }
    }

    "a database whose Flyway history table was emptied is refused, its data untouched" {
      withDatabase { ds ->
        Dbms.migrate(ds)
        ds.addWalk()
        ds.exec("delete from public.flyway_schema_history")

        shouldThrow<IllegalStateException> { Dbms.migrate(ds) }.message.orEmpty() shouldContain "Flyway would run V001"
        ds.walkCounts() shouldBe (1L to 1L)
        ds.count("public.flyway_schema_history") shouldBe 0
      }
    }

    "restoring Flyway's history table lets a refused database start again" {
      withDatabase { ds ->
        Dbms.migrate(ds)
        ds.addWalk()
        val history = ds.flywayHistory()
        ds.exec("alter table public.flyway_schema_history rename to flyway_schema_history_saved")
        shouldThrow<IllegalStateException> { Dbms.migrate(ds) }

        ds.exec("alter table public.flyway_schema_history_saved rename to flyway_schema_history")
        Dbms.migrate(ds).migrationsExecuted shouldBe 0
        ds.flywayHistory() shouldBe history
        ds.walkCounts() shouldBe (1L to 1L)
      }
    }

    "a deliberate baseline at the latest migration lets a refused database start again" {
      withDatabase { ds ->
        Dbms.migrate(ds)
        ds.addWalk()
        ds.exec("drop table public.flyway_schema_history")
        val latest = latestMigration(ds)
        shouldThrow<IllegalStateException> { Dbms.migrate(ds) }.message.orEmpty() shouldContain
          "latest migration ($latest)"

        flyway(ds).baselineVersion(latest).load().baseline()
        Dbms.migrate(ds).migrationsExecuted shouldBe 0
        ds.walkCounts() shouldBe (1L to 1L)
      }
    }
  }
}
