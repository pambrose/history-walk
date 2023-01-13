package com.github.pambrose

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.config.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KLogging
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.time.Duration.Companion.minutes


object Dbms : KLogging() {

  fun init(config: ApplicationConfig) {
    Database.connect(hikari(config))
  }

  private fun hikari(config: ApplicationConfig) =
    HikariConfig()
      .apply {
        driverClassName = EnvVar.DBMS_DRIVER_CLASSNAME.getEnv(Property.DBMS_DRIVER_CLASSNAME.getRequiredProperty())
        jdbcUrl = EnvVar.DBMS_URL.getEnv(Property.DBMS_URL.getRequiredProperty())
        logger.info { "************ jdbcUrl: [$jdbcUrl]" }
        username = EnvVar.DBMS_USERNAME.getEnv(Property.DBMS_USERNAME.getRequiredProperty())
        logger.info { "************ username: [$username]" }
        password = EnvVar.DBMS_PASSWORD.getEnv(Property.DBMS_PASSWORD.getRequiredProperty())
        logger.info { "************ password: [$password]" }
        maximumPoolSize = Property.DBMS_MAX_POOL_SIZE.getRequiredProperty().toInt()

        //isAutoCommit = false
        transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        maxLifetime = Property.DBMS_MAX_LIFETIME_MINS.getRequiredProperty().toInt().minutes.inWholeMilliseconds
        validate()
      }.let { HikariDataSource(it) }

  suspend fun <T> dbQuery(block: Transaction.() -> T): T =
    withContext(Dispatchers.IO) {
      transaction {
        block()
      }
    }
}
