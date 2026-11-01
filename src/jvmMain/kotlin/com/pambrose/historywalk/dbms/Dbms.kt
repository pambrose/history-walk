package com.pambrose.historywalk.dbms

import com.pambrose.historywalk.EnvVar
import com.pambrose.historywalk.Property
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.config.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.flywaydb.core.api.output.MigrateResult
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import javax.sql.DataSource
import kotlin.time.Duration.Companion.minutes

object Dbms {
  private val logger = KotlinLogging.logger {}

  fun dbmsInit(config: ApplicationConfig) {
    val dataSource = hikari(config)
    migrate(dataSource)
    Database.connect(dataSource)
  }

  // Applies any pending migrations in src/jvmMain/resources/db/migration before the app touches the database.
  // Flyway keeps its schema history table in the connection's default schema (public), outside the history schema.
  internal fun migrate(dataSource: DataSource): MigrateResult {
    val flyway =
      Flyway
        .configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .load()
    refuseToDropHistory(flyway, dataSource)
    return flyway
      .migrate()
      .also { result ->
        logger.info {
          "Flyway applied ${result.migrationsExecuted} migration(s); " +
            "schema version ${result.initialSchemaVersion ?: "none"} -> ${result.targetSchemaVersion ?: "unchanged"}"
        }
      }
  }

  /**
   * Fails startup, before Flyway changes anything, when V001 is about to run on a database whose history schema
   * already holds tables. V001 begins with `drop schema if exists history cascade`, and Flyway runs it whenever its
   * schema history table is missing or empty: its own refusal to migrate a non-empty schema without that table looks
   * only at the schema the table lives in (public), which holds nothing else. So a database whose history schema
   * survived without Flyway's history (a restore or copy that left out that table, or the table dropped or emptied)
   * would lose every user and choice. A fresh database has no history schema and a migrated one has V001 recorded, so
   * neither is stopped; nothing here writes to the database.
   */
  private fun refuseToDropHistory(
    flyway: Flyway,
    dataSource: DataSource,
  ) {
    val info = flyway.info()
    if (info.pending().none { it.version == MigrationVersion.fromVersion("1") }) return

    // Flyway looks for its table in current_schema, so the message names the table it did not find
    val (flywaySchema, historyHasTables) =
      dataSource.connection.use { c ->
        c.createStatement().use { st ->
          st
            .executeQuery(
              "select current_schema(), exists (select 1 from pg_catalog.pg_tables where schemaname = 'history')",
            ).use { rs ->
              rs.next()
              rs.getString(1) to rs.getBoolean(2)
            }
        }
      }
    if (!historyHasTables) return

    val table = "$flywaySchema.${flyway.configuration.table}"
    val latest = info.all().mapNotNull { it.version }.max()
    error(
      "Refusing to migrate: the history schema holds tables, but Flyway's schema history table $table is missing or " +
        "empty (a restore or copy that left it out, or the table dropped or emptied), so Flyway would run V001, " +
        "which drops the history schema with every user and choice in it. Restore $table from the same backup as " +
        "the history schema. Or, if the history schema is known to be at the latest migration ($latest), record " +
        "that deliberately with Flyway's baseline command at baselineVersion $latest. To start everyone over " +
        "instead, drop the history schema by hand.",
    )
  }

  private fun hikari(config: ApplicationConfig) =
    HikariConfig()
      .apply {
        val herokuDbmsUrl = System.getenv("DATABASE_URL").orEmpty()
        if (herokuDbmsUrl.isNotEmpty()) {
          // DATABASE_URL holds the password, and the logs go to Papertrail, so only the description is logged.
          val heroku = HerokuDatabaseUrl.parse(herokuDbmsUrl)
          logger.info { "Heroku database: ${heroku.description}" }

          username = heroku.username
          password = heroku.password
          jdbcUrl = heroku.jdbcUrl
        } else {
          username = EnvVar.DBMS_USERNAME.getEnv(Property.DBMS_USERNAME.getRequiredProperty())
          password = EnvVar.DBMS_PASSWORD.getEnv(Property.DBMS_PASSWORD.getRequiredProperty())
          jdbcUrl = EnvVar.DBMS_URL.getEnv(Property.DBMS_URL.getRequiredProperty())
        }

        driverClassName = EnvVar.DBMS_DRIVER_CLASSNAME.getEnv(Property.DBMS_DRIVER_CLASSNAME.getRequiredProperty())
        maximumPoolSize = Property.DBMS_MAX_POOL_SIZE.getRequiredProperty().toInt()

        // isAutoCommit = false
        transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        maxLifetime = Property.DBMS_MAX_LIFETIME_MINS
          .getRequiredProperty()
          .toInt()
          .minutes.inWholeMilliseconds
        validate()
      }.let { HikariDataSource(it) }

  suspend fun <T> dbQuery(block: JdbcTransaction.() -> T): T =
    withContext(Dispatchers.IO) {
      transaction {
        block()
      }
    }
}
