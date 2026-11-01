package com.pambrose.historywalk

import com.pambrose.historywalk.AlertLevel.DIALOG
import com.pambrose.historywalk.AlertLevel.NONE
import com.pambrose.historywalk.AlertLevel.RELOAD
import com.pambrose.historywalk.ServerVersionCheck.RELOAD_COUNTDOWN
import com.pambrose.historywalk.ServerVersionCheck.WARNED_CHECK_INTERVAL
import com.pambrose.historywalk.ServerVersionCheck.checkServerVersion
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ServerVersionCheckTest : StringSpec() {
  private val now = Instant.parse("2026-11-01T10:00:00Z")
  private val warning = 60.seconds
  private val interval = 2.minutes

  private fun check(
    clientApiVersion: String,
    warnedAt: Instant? = null,
  ) = checkServerVersion(
    VersionCheckInfo(version = "1.0.0", apiVersion = clientApiVersion, reloadWarningInstant = warnedAt),
    serverApiVersion = "2.0.0",
    warningInterval = warning,
    checkInterval = interval,
    now = now,
  )

  init {
    "a page on the server's apiVersion gets no alert, and the configured check interval" {
      val response = check("2.0.0")
      response.alertLevel shouldBe NONE
      response.versionCheckInterval shouldBe interval
      response.reloadWarningInstant shouldBe null
    }

    "a page on another apiVersion is first asked to reload in a dialog, and the warning starts now" {
      val response = check("1.0.0")
      response.alertLevel shouldBe DIALOG
      response.message shouldContain "<a href='/'>"
      response.dialogConfig?.rich shouldBe true
      response.reloadWarningInstant shouldBe now
      response.versionCheckInterval shouldBe WARNED_CHECK_INTERVAL
    }

    "the dialog keeps the first warning's time until the warning interval has passed" {
      val warnedAt = now - 30.seconds
      val response = check("1.0.0", warnedAt)
      response.alertLevel shouldBe DIALOG
      response.reloadWarningInstant shouldBe warnedAt
    }

    "once the warning interval has passed, the page counts down and reloads itself" {
      val response = check("1.0.0", now - warning)
      response.alertLevel shouldBe RELOAD
      response.message shouldContain "{{COUNT}}"
      response.toastConfig?.duration shouldBe RELOAD_COUNTDOWN
      response.reloadWarningInstant shouldBe null
    }

    // The RPC carries these as JSON, including the Instant and Duration fields
    "a check and its answer survive the trip as JSON" {
      val info = VersionCheckInfo("1.0.0", "1.0.0", now)
      Json.decodeFromString<VersionCheckInfo>(Json.encodeToString(info)) shouldBe info

      val response = check("1.0.0", now - 10.seconds)
      Json.decodeFromString<VersionCheckResponse>(Json.encodeToString(response)) shouldBe response
    }
  }
}
