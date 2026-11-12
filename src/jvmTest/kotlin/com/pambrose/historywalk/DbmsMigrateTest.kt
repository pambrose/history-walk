package com.pambrose.historywalk

import com.pambrose.historywalk.content.jermainLonguenSlides
import com.pambrose.historywalk.content.mosesSlides
import com.pambrose.historywalk.content.reachableSlides
import com.pambrose.historywalk.dbms.Dbms
import com.zaxxer.hikari.HikariDataSource
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeGreaterThan
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

  // The path names a student could reach in a deck before V005 renamed and rerouted slides: the snapshot as it was then
  private fun pathsBeforeV005(deck: String) =
    javaClass
      .getResource("/slide-paths/before-v005/$deck.txt")!!
      .readText()
      .lines()
      .filter { it.isNotBlank() }

  private fun String.parentPath() = substringBeforeLast('/')

  private fun String.lastTitle() = substringAfterLast('/')

  // Stores a student at each of [places], and one student who made every move between them, each the way the server
  // stores a move: from a slide to its child, with both titles
  private fun DataSource.addPlacesAndMoves(
    deck: String,
    places: List<String>,
  ) = connection.use { c ->
    val addUser =
      "insert into history.users (id, email, full_name, role, last_path_name) values (?::uuid, ?, ?, 'student', ?)"
    c.prepareStatement(addUser).use { st ->
      (places + places.first()).forEachIndexed { i, place ->
        st.setString(1, UUID.randomUUID().toString())
        st.setString(2, "27$deck$i@athenian.org")
        st.setString(3, "$deck $i")
        st.setString(4, place)
        st.addBatch()
      }
      st.executeBatch()
    }
    val addMove =
      "insert into history.user_choices " +
        "(user_uuid_ref, from_path_name, from_title, to_path_name, to_title, dead_end, choice_text, reason) " +
        "select id, ?, ?, ?, ?, false, 'a choice', 'a reason of five words' from history.users where email = ?"
    c.prepareStatement(addMove).use { st ->
      places.filter { it.count { ch -> ch == '/' } > 1 }.forEach { to ->
        st.setString(1, to.parentPath())
        st.setString(2, to.parentPath().lastTitle())
        st.setString(3, to)
        st.setString(4, to.lastTitle())
        st.setString(5, "27$deck${places.size}@athenian.org")
        st.addBatch()
      }
      st.executeBatch()
    }
  }

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

    "V004 marks a stored decision that led to a Continue on to a dead end as a dead end, and nothing else" {
      withDatabase { ds ->
        flyway(ds).target("3").load().migrate()
        val user = UUID.randomUUID()
        val toWhenToGo = "/UGRR Scenario/START: Major Decision/Decision: When to Go"
        ds.exec(
          "insert into history.users (id, email, full_name, role, last_path_name) " +
            "values ('$user', '27autumn@athenian.org', 'Autumn', 'student', '$toWhenToGo/Autumn Departure')",
        )
        listOf("Autumn Departure", "Summer Departure").forEach { to ->
          ds.exec(
            "insert into history.user_choices " +
              "(user_uuid_ref, from_path_name, from_title, to_path_name, to_title, dead_end, choice_text, reason) " +
              "values ('$user', '$toWhenToGo', 'Decision: When to Go', '$toWhenToGo/$to', '$to', false, 'leave', " +
              "'because it is the best time')",
          )
        }

        Dbms.migrate(ds)
        ds.query("select to_title, dead_end from history.user_choices order by to_title") {
          it.getString(1) to it.getBoolean(2)
        } shouldBe listOf("Autumn Departure" to true, "Summer Departure" to false)
      }
    }

    "every place either deck had before V005 renamed and rerouted slides still finds its slide after the migrations" {
      withDatabase { ds ->
        flyway(ds).target("4").load().migrate()
        val decks = mapOf("jermain" to jermainLonguenSlides, "moses" to mosesSlides)
        val before = decks.keys.associateWith { pathsBeforeV005(it) }
        before.forEach { (deck, places) -> ds.addPlacesAndMoves(deck, places) }

        Dbms.migrate(ds)

        val paths = decks.mapValues { (_, deck) -> deck.reachableSlides().map { it.pathName }.toSet() }
        val places = ds.query("select email, last_path_name from history.users") { it.getString(1) to it.getString(2) }
        places.size shouldBe before.values.sumOf { it.size + 1 }
        places
          .filterNot { (email, place) -> place in paths.getValue(if ("jermain" in email) "jermain" else "moses") }
          .shouldBeEmpty()

        // A move is moved whole, to a step from a slide to its child with both titles, or left as it was, when the move
        // was on a branch that is gone; never half moved
        val allPaths = paths.values.flatten().toSet()
        ds
          .query("select from_path_name, from_title, to_path_name, to_title from history.user_choices") { rs ->
            listOf(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4))
          }.filterNot { (from, fromTitle, to, toTitle) ->
            val moved =
              from in allPaths && to in allPaths && to.parentPath() == from &&
                fromTitle == from.lastTitle() && toTitle == to.lastTitle()
            moved || (from !in allPaths && to !in allPaths)
          }.shouldBeEmpty()

        val start = "/UGRR Scenario/START: Major Decision/Companion Decision"
        val goToOldBen = "$start/Go To Old Ben"
        val refusedRoss = "$start/Ross Encounter, then Ben/Go To Old Ben"
        val movesFrom = { from: String ->
          ds.query("select to_path_name, to_title from history.user_choices where from_path_name = '$from'") {
            it.getString(1) to it.getString(2)
          }
        }
        // Spring on Old Ben's own advice now goes through Spring Departure, and refusing Ross leads to that advice
        movesFrom(goToOldBen).toSet() shouldContainAll setOf("$goToOldBen/Spring Departure" to "Spring Departure")
        movesFrom("$start/Ross Encounter, then Ben").toSet() shouldContainAll setOf(refusedRoss to "Go To Old Ben")
        // A student on the gone via-Ross winter of that branch chooses when to go again
        val choosingAgain = "select count(*) from history.users where last_path_name = '$refusedRoss'"
        ds.query(choosingAgain) { it.getLong(1) }.single() shouldBeGreaterThan 1
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
