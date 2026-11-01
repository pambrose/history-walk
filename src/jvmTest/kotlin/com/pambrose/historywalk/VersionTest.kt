package com.pambrose.historywalk

import com.pambrose.BuildConfig
import com.pambrose.common.util.Version.Companion.versionDesc
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain

class VersionTest : StringSpec() {
  init {
    "the server's version description comes from BuildConfig" {
      val desc = HistoryWalkServer::class.versionDesc()
      desc shouldContain "Version: ${BuildConfig.VERSION}"
      desc shouldContain "Release Date: ${BuildConfig.RELEASE_DATE}"
    }
  }
}
