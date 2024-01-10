package com.github.pambrose

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.config.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.two.KLogging
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.time.Duration.Companion.minutes

object Dbms : KLogging() {
  fun dbmsInit(config: ApplicationConfig) {
    Database.connect(hikari(config))
  }

  private fun hikari(config: ApplicationConfig) =
    HikariConfig()
      .apply {
        val herokuDbmsUrl = System.getenv("DATABASE_URL").orEmpty()
        if (herokuDbmsUrl.isNotEmpty()) {
          logger.info { "Heroku database url: $herokuDbmsUrl" }

          username = herokuDbmsUrl.substringAfter("://").substringBefore(":")
          password = herokuDbmsUrl.substringAfter("://$username:").substringBefore("@")
          val dbName = herokuDbmsUrl.substringAfterLast("/")
          val host = herokuDbmsUrl.substringAfter("@").substringBefore(":")
          val port = herokuDbmsUrl.substringAfterLast(":").substringBefore("/")

          jdbcUrl = "jdbc:pgsql://$host:$port/$dbName?sslmode=require"
          logger.info { "Actual database url: $jdbcUrl" }
        } else {
          username = EnvVar.DBMS_USERNAME.getEnv(Property.DBMS_USERNAME.getRequiredProperty())
          password = EnvVar.DBMS_PASSWORD.getEnv(Property.DBMS_PASSWORD.getRequiredProperty())
          jdbcUrl = EnvVar.DBMS_URL.getEnv(Property.DBMS_URL.getRequiredProperty())
        }

        driverClassName = EnvVar.DBMS_DRIVER_CLASSNAME.getEnv(Property.DBMS_DRIVER_CLASSNAME.getRequiredProperty())
        maximumPoolSize = Property.DBMS_MAX_POOL_SIZE.getRequiredProperty().toInt()

        // isAutoCommit = false
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
