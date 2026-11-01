package com.pambrose.historywalk

import com.pambrose.historywalk.dbms.Dbms
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.testcontainers.postgresql.PostgreSQLContainer

/**
 * A Postgres 18 container with the Flyway migrations applied, made Exposed's default database. Each spec that uses
 * one starts its own in beforeSpec and closes it in afterSpec, so specs never see each other's rows.
 */
class TestPostgres : AutoCloseable {
  private val container = PostgreSQLContainer("postgres:18").also { it.start() }

  val dataSource =
    HikariDataSource(
      HikariConfig().apply {
        jdbcUrl = container.jdbcUrl
        username = container.username
        password = container.password
        driverClassName = "org.postgresql.Driver"
        maximumPoolSize = 4
      },
    )

  init {
    Dbms.migrate(dataSource)
    TransactionManager.defaultDatabase = Database.connect(dataSource)
  }

  /** Empties the tables, for specs whose tests read every row through the progress views. */
  fun deleteAll() {
    dataSource.connection.use { c ->
      c.createStatement().use { st -> st.execute("delete from history.user_choices; delete from history.users") }
    }
  }

  override fun close() {
    dataSource.close()
    container.stop()
  }
}
