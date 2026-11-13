package com.pambrose.historywalk

import com.pambrose.common.util.isNotNull
import com.pambrose.common.util.obfuscate

/**
 * The settings the server reads from the environment, each from the variable of its own name. [maskFunc] gives the
 * value as a log line may show it, obfuscated for a secret.
 */
enum class EnvVar(
  val maskFunc: EnvVar.() -> String = { getEnv(UNASSIGNED) },
) {
  SHOW_RESET_BUTTON,
  DISPLAY_CONSECUTIVE_CORRECT_DECISIONS,
  ALLOW_SLIDE_ACCESS,
  SLIDES_VARIABLE_NAME,
  DBMS_DRIVER_CLASSNAME,
  DBMS_NAME,
  DBMS_QUERY_PATH({ getEnvOrNull()?.obfuscate(4) ?: UNASSIGNED }),
  DBMS_URL({ getEnvOrNull()?.obfuscate(4) ?: UNASSIGNED }),
  DBMS_USERNAME,
  DBMS_PASSWORD({ getEnvOrNull()?.obfuscate(1) ?: UNASSIGNED }),
  GOOGLE_CLIENT_ID,
  GOOGLE_CLIENT_SECRET({ getEnvOrNull()?.obfuscate(1) ?: UNASSIGNED }),
  OAUTH_CALLBACK_URL,
  SESSION_SECRET({ getEnvOrNull()?.obfuscate(1) ?: UNASSIGNED }),
  SCHOOL_EMAIL_DOMAIN,
  TEACHER_EMAILS,
  LOGIN_GREETING,
  CLIENT_VERSION_CHECK_INTERVAL_SECS,
  CLIENT_RELOAD_WARNING_INTERVAL_SECS,
  ;

  /** Whether the variable is set, even to a blank value. */
  fun isDefined(): Boolean = getEnvOrNull().isNotNull()

  /** The variable's value, or null when it is not set. */
  fun getEnvOrNull(): String? = System.getenv(name)

  /** The variable's value, or [default] when it is not set. A blank value is returned as it is. */
  fun getEnv(default: String) = System.getenv(name) ?: default

  /** `true` or `false`, in any case; [default] when unset or blank. Anything else fails, naming the setting. */
  fun getEnv(default: Boolean): Boolean = parseBoolean(name, getEnvOrNull(), default)

  /** A whole number in [range]; [default] when unset or blank. Anything else fails, naming the setting and the range. */
  fun getEnv(
    default: Int,
    range: IntRange,
  ): Int = parseInt(name, getEnvOrNull(), default, range)

  /** The variable's value; fails, naming the variable, when it is not set. */
  fun getRequiredEnv() = getEnvOrNull() ?: error("Missing $name value")

  /** The strict readings of a boolean or number setting, kept apart from the environment so tests can drive them. */
  companion object {
    /**
     * The boolean setting [name] holds as [raw]. `toBoolean()` took anything but `true` for false, so `1` or `yes`
     * silently turned a setting off; such a value now stops the server at startup ([verifySettings]).
     */
    internal fun parseBoolean(
      name: String,
      raw: String?,
      default: Boolean,
    ): Boolean =
      when (raw?.trim()?.lowercase()) {
        null, "" -> default

        "true" -> true

        "false" -> false

        else -> throw IllegalArgumentException(
          "$name is \"$raw\": set it to true or false, or leave it unset for $default",
        )
      }

    /** The number setting [name] holds as [raw], which must be a whole number in [range]. */
    internal fun parseInt(
      name: String,
      raw: String?,
      default: Int,
      range: IntRange,
    ): Int {
      val text = raw?.trim().orEmpty()
      if (text.isEmpty()) return default
      return text.toIntOrNull()?.takeIf { it in range }
        ?: throw IllegalArgumentException(
          "$name is \"$raw\": set it to a whole number from ${range.first} to ${range.last}, or leave it unset for " +
            "$default",
        )
    }
  }
}

/**
 * Fails startup, naming the setting, when a boolean or number setting holds a value it cannot use. Each is otherwise
 * read only when first needed, a version-check setting at a page's first check, so a typo would surface only then, as
 * a failure on every check, and a version-check interval of 0 would have every open page poll in a tight loop.
 */
fun verifySettings() {
  listOf(EnvVar.SHOW_RESET_BUTTON, EnvVar.DISPLAY_CONSECUTIVE_CORRECT_DECISIONS, EnvVar.ALLOW_SLIDE_ACCESS)
    .forEach { it.getEnv(false) }
  ServerVersionCheck.versionCheckInterval
  ServerVersionCheck.reloadWarningInterval
}
