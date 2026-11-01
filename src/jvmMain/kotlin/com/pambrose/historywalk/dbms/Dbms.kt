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
  internal fun migrate(dataSource: DataSource) {
    Flyway
      .configure()
      .dataSource(dataSource)
      .locations("classpath:db/migration")
      .load()
      .migrate()
      .also { result ->
        logger.info {
          "Flyway applied ${result.migrationsExecuted} migration(s); " +
            "schema version ${result.initialSchemaVersion ?: "none"} -> ${result.targetSchemaVersion ?: "unchanged"}"
        }
      }
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
