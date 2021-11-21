package com.github.pambrose

import com.github.pambrose.common.util.isNotNull
import com.github.pambrose.common.util.obfuscate

enum class EnvVar(val maskFunc: EnvVar.() -> String = { getEnv(UNASSIGNED) }) {

  DBMS_DRIVER_CLASSNAME,
  DBMS_URL({ getEnvOrNull()?.obfuscate(4) ?: UNASSIGNED }),
  DBMS_USERNAME,
  DBMS_PASSWORD({ getEnvOrNull()?.obfuscate(1) ?: UNASSIGNED });

  fun isDefined(): Boolean = getEnvOrNull().isNotNull()

  fun getEnvOrNull(): String? = System.getenv(name)

  fun getEnv(default: String) = System.getenv(name) ?: default

  fun getEnv(default: Boolean) = System.getenv(name)?.toBoolean() ?: default

  @Suppress("unused")
  fun getEnv(default: Int) = System.getenv(name)?.toInt() ?: default

  fun getRequiredEnv() = getEnvOrNull() ?: error("Missing $name value")
}