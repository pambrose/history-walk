package com.github.pambrose

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.config.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.time.Duration.Companion.minutes


object Db {
  fun init(config: ApplicationConfig) {
    Database.connect(hikari(config))
  }

  private fun hikari(config: ApplicationConfig) =
    HikariDataSource(
      HikariConfig()
        .apply {
          driverClassName = EnvVar.DBMS_DRIVER_CLASSNAME.getEnv(Property.DBMS_DRIVER_CLASSNAME.getRequiredProperty())
          jdbcUrl = EnvVar.DBMS_URL.getEnv(Property.DBMS_URL.getRequiredProperty())
          username = EnvVar.DBMS_USERNAME.getEnv(Property.DBMS_USERNAME.getRequiredProperty())
          password = EnvVar.DBMS_PASSWORD.getEnv(Property.DBMS_PASSWORD.getRequiredProperty())

          maximumPoolSize = Property.DBMS_MAX_POOL_SIZE.getRequiredProperty().toInt()
          isAutoCommit = false
          transactionIsolation = "TRANSACTION_REPEATABLE_READ"
          maxLifetime =
            minutes(Property.DBMS_MAX_LIFETIME_MINS.getRequiredProperty().toInt()).inWholeMilliseconds
          validate()
        })

  private fun hikari2(config: ApplicationConfig): HikariDataSource {
    val hikariConfig = HikariConfig()
    hikariConfig.driverClassName = config.propertyOrNull("db.driver")?.getString() ?: "org.h2.Driver"
    hikariConfig.jdbcUrl = config.propertyOrNull("db.jdbcUrl")?.getString() ?: "jdbc:h2:mem:test"
    hikariConfig.username = config.propertyOrNull("db.username")?.getString()
    hikariConfig.password = config.propertyOrNull("db.password")?.getString()
    hikariConfig.maximumPoolSize = 3
    hikariConfig.isAutoCommit = false
    hikariConfig.transactionIsolation = "TRANSACTION_REPEATABLE_READ"
    hikariConfig.validate()
    return HikariDataSource(hikariConfig)
  }

  suspend fun <T> dbQuery(block: Transaction.() -> T): T = withContext(Dispatchers.IO) {
    transaction {
      block()
    }
  }
}
