package com.github.pambrose

import com.github.pambrose.common.script.KotlinScriptPool
import mu.KLogging

object ScriptPools : KLogging() {

  internal val kotlinScriptPool by lazy {
    KotlinScriptPool(5, true)
      .also { logger.info { "Created Kotlin script pool with size ${it.size}" } }
  }

}