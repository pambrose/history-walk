package com.pambrose.historywalk

import com.pambrose.common.util.isNotNull
import com.pambrose.common.util.obfuscate

enum class EnvVar(val maskFunc: EnvVar.() -> String = { getEnv(UNASSIGNED) }) {
  SHOW_RESET_BUTTON,
  DISPLAY_CONSECUTIVE_CORRECT_DECISIONS,
  ALLOW_SLIDE_ACCESS,
  SLIDES_LOCAL_FILENAME,
  SLIDES_REPO_TYPE,
  SLIDES_REPO_OWNER,
  SLIDES_REPO_NAME,
  SLIDES_REPO_BRANCH,
  SLIDES_REPO_PATH,
  SLIDES_REPO_FILENAME,
  SLIDES_VARIABLE_NAME,
  DBMS_DRIVER_CLASSNAME,
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
  ;

  fun isDefined(): Boolean = getEnvOrNull().isNotNull()

  fun getEnvOrNull(): String? = System.getenv(name)

  fun getEnv(default: String) = System.getenv(name) ?: default

  fun getEnv(default: Boolean) = System.getenv(name)?.toBoolean() ?: default

  @Suppress("unused")
  fun getEnv(default: Int) = System.getenv(name)?.toInt() ?: default

  fun getRequiredEnv() = getEnvOrNull() ?: error("Missing $name value")
}
