package com.github.pambrose

import com.github.pambrose.PropertyNames.DBMS
import com.github.pambrose.PropertyNames.HISTORYWALK
import com.github.pambrose.PropertyNames.SITE
import com.github.pambrose.common.util.isNotNull
import com.github.pambrose.common.util.obfuscate
import io.ktor.server.application.*
import io.ktor.server.config.*
import mu.two.KLogging
import java.util.concurrent.atomic.AtomicBoolean

object PropertyNames {
  const val HISTORYWALK = "historywalk"
  const val DBMS = "dbms"
  const val SITE = "site"
}

enum class Property(
  private val propertyValue: String,
  val maskFunc: Property.() -> String = {
    getProperty(
      UNASSIGNED,
      false,
    )
  },
) {
  ANALYTICS_ID(
    "$HISTORYWALK.$SITE.googleAnalyticsId",
    { getPropertyOrNull(false) ?: UNASSIGNED },
  ),
  KTOR_PORT("ktor.deployment.port"),
  KTOR_WATCH("ktor.deployment.watch"),

  DBMS_DRIVER_CLASSNAME("$DBMS.driverClassName"),
  DBMS_URL("$DBMS.jdbcUrl"),
  DBMS_USERNAME("$DBMS.username"),
  DBMS_PASSWORD(
    "$DBMS.password",
    { getPropertyOrNull(false)?.obfuscate(1) ?: UNASSIGNED },
  ),
  DBMS_MAX_POOL_SIZE("$DBMS.maxPoolSize"),
  DBMS_MAX_LIFETIME_MINS("$DBMS.maxLifetimeMins"),
  ;

  private fun Application.configProperty(
    name: String,
    default: String = "",
    warn: Boolean = false,
  ): String =
    try {
      environment.config.property(name).getString()
    } catch (e: Throwable) {
      if (warn)
        logger.warn { "Missing $name value in application.conf" }
      default
    }

  fun configValue(
    application: Application,
    default: String = "",
    warn: Boolean = false,
  ): String =
    application.configProperty(
      propertyValue,
      default,
      warn,
    )

  fun configValueOrNull(application: Application): ApplicationConfigValue? =
    application.environment.config.propertyOrNull(propertyValue)

  fun getProperty(
    default: String,
    errorOnNonInit: Boolean = true,
  ): String =
    (
      System.getProperty(propertyValue) ?: default
      )
      .also { if (errorOnNonInit && !initialized.get()) error(notInitialized(this)) }

  fun getProperty(default: Boolean) =
    (
      System.getProperty(propertyValue)?.toBoolean() ?: default
      )
      .also { if (!initialized.get()) error(notInitialized(this)) }

  fun getProperty(default: Int) =
    (
      System.getProperty(propertyValue)?.toIntOrNull() ?: default
      )
      .also { if (!initialized.get()) error(notInitialized(this)) }

  fun getPropertyOrNull(errorOnNonInit: Boolean = true): String? =
    System.getProperty(propertyValue).also { if (errorOnNonInit && !initialized.get()) error(notInitialized(this)) }

  fun getRequiredProperty() =
    (
      getPropertyOrNull() ?: error("Missing $propertyValue value")
      )
      .also { if (!initialized.get()) error(notInitialized(this)) }

  fun setProperty(value: String) {
    System.setProperty(
      propertyValue,
      value,
    )
    logger.info { "$propertyValue: ${maskFunc()}" }
  }

  fun setPropertyFromConfig(
    application: Application,
    default: String,
  ) {
    if (isNotDefined())
      setProperty(
        configValue(
          application,
          default,
        ),
      )
  }

  fun isDefined() = System.getProperty(propertyValue).isNotNull()

  fun isNotDefined() = !isDefined()

  companion object : KLogging() {
    private val initialized = AtomicBoolean(false)

    fun assignInitialized() = initialized.set(true)

    private fun notInitialized(prop: Property) = "Property ${prop.name} not initialized"

    fun Application.assignProperties() {
      ANALYTICS_ID.setPropertyFromConfig(
        this,
        "",
      )

      DBMS_DRIVER_CLASSNAME.setPropertyFromConfig(
        this,
        "com.impossibl.postgres.jdbc.PGDriver",
      )
      DBMS_URL.setPropertyFromConfig(
        this,
        "jdbc:pgsql://localhost:5432/historywalk",
      )
      DBMS_USERNAME.setPropertyFromConfig(
        this,
        "postgres",
      )
      DBMS_PASSWORD.setPropertyFromConfig(
        this,
        "docker",
      )
      DBMS_MAX_POOL_SIZE.setPropertyFromConfig(
        this,
        "10",
      )
      DBMS_MAX_LIFETIME_MINS.setPropertyFromConfig(
        this,
        "30",
      )

      KTOR_PORT.setPropertyFromConfig(
        this,
        "0",
      )
      KTOR_WATCH.also { it.setProperty(it.configValueOrNull(this)?.getList()?.toString() ?: UNASSIGNED) }

      assignInitialized()
    }
  }
}
