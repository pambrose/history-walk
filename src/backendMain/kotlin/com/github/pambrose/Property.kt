package com.github.pambrose

import com.github.pambrose.PropertyNames.AGENT
import com.github.pambrose.PropertyNames.CONTENT
import com.github.pambrose.PropertyNames.DBMS
import com.github.pambrose.PropertyNames.READINGBAT
import com.github.pambrose.PropertyNames.SITE
import com.github.pambrose.common.util.isNotNull
import com.github.pambrose.common.util.obfuscate
import io.ktor.application.*
import io.ktor.config.*
import mu.KLogging
import java.util.concurrent.atomic.AtomicBoolean

object PropertyNames {
  const val READINGBAT = "readingbat"
  const val DBMS = "dbms"
  const val SITE = "site"
  const val AGENT = "agent"
  const val CLASSES = "classes"
  const val CONTENT = "content"
  const val CHALLENGES = "challenges"
}

enum class Property(
  private val propertyValue: String,
  val maskFunc: Property.() -> String = { getProperty(UNASSIGNED, false) }
) {
  KOTLIN_SCRIPT_CLASSPATH("kotlin.script.classpath"),

  CONFIG_FILENAME("$READINGBAT.configFilename"),
  ADMIN_USERS("$READINGBAT.adminUsers"),
  LOGBACK_CONFIG_FILE("logback.configurationFile"),
  AGENT_LAUNCH_ID("$AGENT.launchId"),

  AGENT_CONFIG("agent.config"),

  // These are used in module()
  DSL_FILE_NAME("$READINGBAT.$CONTENT.fileName"),
  DSL_VARIABLE_NAME("$READINGBAT.$CONTENT.variableName"),
  PROXY_HOSTNAME("$AGENT.proxy.hostname"),
  STARTUP_DELAY_SECS("$READINGBAT.$SITE.startupMaxDelaySecs"),

  // These are defaults for env var values
  REDIRECT_HOSTNAME("$READINGBAT.$SITE.redirectHostname"),
  SENDGRID_PREFIX("$READINGBAT.$SITE.sendGridPrefix"),
  FORWARDED_ENABLED("$READINGBAT.$SITE.forwardedHeaderSupportEnabled"),
  XFORWARDED_ENABLED("$READINGBAT.$SITE.xforwardedHeaderSupportEnabled"),

  // These are assigned to ReadingBatContent vals
  ANALYTICS_ID("$READINGBAT.$SITE.googleAnalyticsId", { getPropertyOrNull(false) ?: UNASSIGNED }),
  KTOR_PORT("ktor.deployment.port"),
  KTOR_WATCH("ktor.deployment.watch"),

  // These are assigned in ReadingBatServer
  IS_PRODUCTION("$READINGBAT.$SITE.production"),
  IS_TESTING("$READINGBAT.$SITE.testing"),
  DBMS_ENABLED("$READINGBAT.$SITE.dbmsEnabled"),
  SAVE_REQUESTS_ENABLED("$READINGBAT.$SITE.saveRequestsEnabled"),

  PINGDOM_BANNER_ID("$READINGBAT.$SITE.pingdomBannerId", { getPropertyOrNull(false) ?: UNASSIGNED }),
  PINGDOM_URL("$READINGBAT.$SITE.pingdomUrl", { getPropertyOrNull(false) ?: UNASSIGNED }),
  STATUS_PAGE_URL("$READINGBAT.$SITE.statusPageUrl", { getPropertyOrNull(false) ?: UNASSIGNED }),

  DBMS_DRIVER_CLASSNAME("$DBMS.driverClassName"),
  DBMS_URL("$DBMS.jdbcUrl"),
  DBMS_USERNAME("$DBMS.username"),
  DBMS_PASSWORD("$DBMS.password", { getPropertyOrNull(false)?.obfuscate(1) ?: UNASSIGNED }),
  DBMS_MAX_POOL_SIZE("$DBMS.maxPoolSize"),
  DBMS_MAX_LIFETIME_MINS("$DBMS.maxLifetimeMins"),
  ;

  private fun Application.configProperty(name: String, default: String = "", warn: Boolean = false) =
    try {
      environment.config.property(name).getString()
    } catch (e: ApplicationConfigurationException) {
      if (warn)
        logger.warn { "Missing $name value in application.conf" }
      default
    }

  fun configValue(application: Application, default: String = "", warn: Boolean = false) =
    application.configProperty(propertyValue, default, warn)

  fun configValueOrNull(application: Application) =
    application.environment.config.propertyOrNull(propertyValue)

  fun getProperty(default: String, errorOnNonInit: Boolean = true) =
    (System.getProperty(propertyValue)
      ?: default).also { if (errorOnNonInit && !initialized.get()) error(notInitialized(this)) }

  fun getProperty(default: Boolean) = (System.getProperty(propertyValue)?.toBoolean()
    ?: default).also { if (!initialized.get()) error(notInitialized(this)) }

  fun getProperty(default: Int) = (System.getProperty(propertyValue)?.toIntOrNull()
    ?: default).also { if (!initialized.get()) error(notInitialized(this)) }

  fun getPropertyOrNull(errorOnNonInit: Boolean = true): String? =
    System.getProperty(propertyValue).also { if (errorOnNonInit && !initialized.get()) error(notInitialized(this)) }

  fun getRequiredProperty() = (getPropertyOrNull()
    ?: error("Missing $propertyValue value")).also { if (!initialized.get()) error(notInitialized(this)) }

  fun setProperty(value: String) {
    System.setProperty(propertyValue, value)
    logger.info { "$propertyValue: ${maskFunc()}" }
  }

  fun setPropertyFromConfig(application: Application, default: String) {
    if (isNotDefined())
      setProperty(configValue(application, default))
  }

  fun isDefined() = System.getProperty(propertyValue).isNotNull()
  fun isNotDefined() = !isDefined()

  companion object : KLogging() {
    private val initialized = AtomicBoolean(false)

    fun assignInitialized() = initialized.set(true)

    private fun notInitialized(prop: Property) = "Property ${prop.name} not initialized"

    fun Application.assignProperties() {

      PROXY_HOSTNAME.setPropertyFromConfig(this, "")

      IS_PRODUCTION.also { it.setProperty(it.configValue(this, "false").toBoolean().toString()) }

      DBMS_ENABLED.also { it.setProperty(it.configValue(this, "false").toBoolean().toString()) }

      SAVE_REQUESTS_ENABLED.also { it.setProperty(it.configValue(this, "true").toBoolean().toString()) }

      DSL_FILE_NAME.setPropertyFromConfig(this, "src/Content.kt")
      DSL_VARIABLE_NAME.setPropertyFromConfig(this, "content")

      ANALYTICS_ID.setPropertyFromConfig(this, "")

      PINGDOM_BANNER_ID.setPropertyFromConfig(this, "")
      PINGDOM_URL.setPropertyFromConfig(this, "")
      STATUS_PAGE_URL.setPropertyFromConfig(this, "")

      DBMS_DRIVER_CLASSNAME.setPropertyFromConfig(this, "com.impossibl.postgres.jdbc.PGDriver")
      DBMS_URL.setPropertyFromConfig(this, "jdbc:pgsql://localhost:5432/history-walk")
      DBMS_USERNAME.setPropertyFromConfig(this, "postgres")
      DBMS_PASSWORD.setPropertyFromConfig(this, "docker")
      DBMS_MAX_POOL_SIZE.setPropertyFromConfig(this, "10")
      DBMS_MAX_LIFETIME_MINS.setPropertyFromConfig(this, "30")

      KTOR_PORT.setPropertyFromConfig(this, "0")
      KTOR_WATCH.also { it.setProperty(it.configValueOrNull(this)?.getList()?.toString() ?: UNASSIGNED) }

      assignInitialized()
    }
  }
}