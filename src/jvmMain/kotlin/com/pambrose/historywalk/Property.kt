package com.pambrose.historywalk

import com.pambrose.common.util.isNotNull
import com.pambrose.common.util.obfuscate
import com.pambrose.historywalk.PropertyNames.DBMS
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.*
import io.ktor.server.config.*
import kotlin.concurrent.atomics.AtomicBoolean

/** The `application.yaml` section that holds the database settings ([DBMS]). */
object PropertyNames {
  const val DBMS = "dbms"
}

/**
 * Server settings copied from `application.yaml` into JVM system properties at startup ([assignProperties]), each under
 * its config key. Reading one before then fails. [maskFunc] gives the value as a log line may show it, obfuscated for
 * a secret.
 */
enum class Property(
  private val propertyValue: String,
  val maskFunc: Property.() -> String = {
    getProperty(
      UNASSIGNED,
      false,
    )
  },
) {
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
    environment.config.propertyOrNull(name)?.getString()
      ?: default.also { if (warn) logger.warn { "Missing $name value in application.yaml" } }

  /** [application]'s config value for this setting, or [default] when there is none, with a warning if [warn]. */
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

  /** The raw config value for this setting in [application], or null when there is none: a list setting reads this. */
  fun configValueOrNull(application: Application): ApplicationConfigValue? =
    application.environment.config.propertyOrNull(propertyValue)

  /**
   * The system property's value, or [default] when it is not set. Fails when read before [assignProperties] has run,
   * unless [errorOnNonInit] is false, as it is for the masked log line written while the properties are being set.
   */
  fun getProperty(
    default: String,
    errorOnNonInit: Boolean = true,
  ): String =
    (
      System.getProperty(propertyValue) ?: default
      ).also { if (errorOnNonInit && !initialized.load()) error(notInitialized(this)) }

  /**
   * The system property as a boolean, or [default] when it is not set: any value but `true`, in any case, is false.
   * Fails when read before [assignProperties] has run.
   */
  fun getProperty(default: Boolean) =
    (
      System.getProperty(propertyValue)?.toBoolean() ?: default
      ).also { if (!initialized.load()) error(notInitialized(this)) }

  /**
   * The system property as a whole number, or [default] when it is not set or not a number. Fails when read before
   * [assignProperties] has run.
   */
  fun getProperty(default: Int) =
    (
      System.getProperty(propertyValue)?.toIntOrNull() ?: default
      ).also { if (!initialized.load()) error(notInitialized(this)) }

  /**
   * The system property's value, or null when it is not set. Fails when read before [assignProperties] has run, unless
   * [errorOnNonInit] is false.
   */
  fun getPropertyOrNull(errorOnNonInit: Boolean = true): String? =
    System.getProperty(propertyValue).also { if (errorOnNonInit && !initialized.load()) error(notInitialized(this)) }

  /** The system property's value; fails, naming its key, when it is not set, or when read before [assignProperties]. */
  fun getRequiredProperty() =
    (
      getPropertyOrNull() ?: error("Missing $propertyValue value")
      ).also { if (!initialized.load()) error(notInitialized(this)) }

  /** Sets the system property to [value] and logs it as [maskFunc] shows it. */
  fun setProperty(value: String) {
    System.setProperty(
      propertyValue,
      value,
    )
    logger.info { "$propertyValue: ${maskFunc()}" }
  }

  /**
   * Sets the system property from [application]'s config, or to [default] when the config has no value, unless it is
   * already set (on the command line, say), in which case that value is kept.
   */
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

  /** Whether the system property is set. Unlike the getters, this works before [assignProperties] has run. */
  fun isDefined() = System.getProperty(propertyValue).isNotNull()

  /** Whether the system property is still unset; [setPropertyFromConfig] sets only such a one. */
  fun isNotDefined() = !isDefined()

  /** Startup: [assignProperties] sets every property, then marks them ready to read. */
  companion object {
    private val logger = KotlinLogging.logger {}
    private val initialized = AtomicBoolean(false)

    /** Marks the properties as set, so reading one no longer fails; [assignProperties] calls it last. */
    fun assignInitialized() = initialized.store(true)

    private fun notInitialized(prop: Property) = "Property ${prop.name} not initialized"

    /**
     * Sets each property from `application.yaml`, or its default (the database ones name a local Postgres), keeping
     * any but `ktor.deployment.watch` already set as a system property, then marks them ready to read. The server's
     * module runs it first.
     */
    fun Application.assignProperties() {
      DBMS_DRIVER_CLASSNAME.setPropertyFromConfig(
        this,
        "org.postgresql.Driver",
      )
      DBMS_URL.setPropertyFromConfig(
        this,
        "jdbc:postgresql://localhost:5432/historywalk",
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
