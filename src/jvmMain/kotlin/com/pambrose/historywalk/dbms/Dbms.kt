package com.pambrose.historywalk.dbms

import com.pambrose.historywalk.EnvVar
import com.pambrose.historywalk.Property
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.config.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.flywaydb.core.api.output.MigrateResult
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import javax.sql.DataSource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

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

  private fun hikari(config: ApplicationConfig): HikariDataSource =
    HikariConfig()
      .apply {
        val containerDbmsUrl = System.getenv("DATABASE_URL").orEmpty()
        if (containerDbmsUrl.isNotEmpty()) {
          // DATABASE_URL holds the password, so only the description is logged.
          val database =
            ContainerDatabaseUrl.parse(
              databaseUrl = containerDbmsUrl,
              dbNameOverride = EnvVar.DBMS_NAME.getEnv(""),
              queryPathOverride = EnvVar.DBMS_QUERY_PATH.getEnv(""),
            )
          logger.info { "Container database: ${database.description}" }

          username = database.username
          password = database.password
          jdbcUrl = database.jdbcUrl
        } else {
          username = EnvVar.DBMS_USERNAME.getEnv(Property.DBMS_USERNAME.getRequiredProperty())
          password = EnvVar.DBMS_PASSWORD.getEnv(Property.DBMS_PASSWORD.getRequiredProperty())
          jdbcUrl = requirePgjdbcUrl(EnvVar.DBMS_URL.getEnv(Property.DBMS_URL.getRequiredProperty()))
        }

        driverClassName = EnvVar.DBMS_DRIVER_CLASSNAME.getEnv(Property.DBMS_DRIVER_CLASSNAME.getRequiredProperty())
        unencryptedUnlessAsked()
        maximumPoolSize = Property.DBMS_MAX_POOL_SIZE.getRequiredProperty().toInt()

        transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        maxLifetime = Property.DBMS_MAX_LIFETIME_MINS
          .getRequiredProperty()
          .toInt()
          .minutes.inWholeMilliseconds
        validate()
      }.let { HikariDataSource(it) }

  // pgjdbc tries SSL first when a URL sets no sslmode (prefer). The connection stays unencrypted unless the URL asks
  // (sslmode=require), as it was under pgjdbc-ng, which suits a platform's private network: this is a driver property,
  // and pgjdbc lets a URL's own parameters win over those.
  internal fun HikariConfig.unencryptedUnlessAsked() = addDataSourceProperty("sslmode", "disable")

  // DBMS_URL must be pgjdbc's, which takes only a URL that starts with jdbc:postgresql:, as written. Hikari would refuse
  // any other only with "Driver org.postgresql.Driver claims to not accept jdbcUrl", which says nothing of the fix, so
  // this says what to change, with a hint of its own for pgjdbc-ng's jdbc:pgsql:, the scheme DBMS_URL took until the
  // app moved to pgjdbc. A refusal repeats no part of the URL, which may hold a password outside a password=
  // parameter, the one part Hikari masks. The spaces around the URL are dropped.
  internal fun requirePgjdbcUrl(jdbcUrl: String): String {
    val url = jdbcUrl.trim()
    require(!url.startsWith("jdbc:pgsql:", ignoreCase = true)) {
      "DBMS_URL starts with jdbc:pgsql:, the scheme of the pgjdbc-ng driver, which History Walk no longer uses. " +
        "Write it as jdbc:postgresql://host:port/database, with any ssl.mode=... parameter as sslmode=..."
    }
    require(url.startsWith("jdbc:postgresql:")) {
      "DBMS_URL must start with jdbc:postgresql:, as in jdbc:postgresql://host:port/database. A platform's " +
        "postgres://user:password@host:port/database URL goes in DATABASE_URL instead."
    }
    return url
  }

  /** How long `/health` waits for the database before it reports it down. */
  val HEALTH_TIMEOUT = 3.seconds

  /** Whether the database answers a trivial query within [HEALTH_TIMEOUT] (see [answersWithin]). */
  suspend fun answers(): Boolean = answersWithin(HEALTH_TIMEOUT) { transaction { exec("select 1") } }

  /**
   * Whether [probe] returns, rather than throws, within [timeout]. It runs on the IO dispatcher, apart from the caller,
   * since JDBC cannot be interrupted: with the database down, Hikari waits up to its connection timeout (30 seconds) for
   * a connection, and the caller is answered at [timeout] while that wait finishes on its own.
   */
  internal suspend fun answersWithin(
    timeout: Duration,
    probe: () -> Unit,
  ): Boolean {
    val check = CoroutineScope(Dispatchers.IO).async { runCatching(probe).isSuccess }
    return withTimeoutOrNull(timeout) { check.await() } ?: false.also { check.cancel() }
  }

  suspend fun <T> dbQuery(block: JdbcTransaction.() -> T): T =
    withContext(Dispatchers.IO) {
      transaction {
        block()
      }
    }
}
