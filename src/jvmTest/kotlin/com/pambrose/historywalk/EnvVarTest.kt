package com.pambrose.historywalk

import com.pambrose.historywalk.EnvVar.Companion.parseBoolean
import com.pambrose.historywalk.EnvVar.Companion.parseInt
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/**
 * The boolean and number settings are read strictly: a value the server cannot use stops it at startup
 * ([verifySettings]), naming the setting, rather than reading `1` as false or failing every version check later.
 */
class EnvVarTest : StringSpec() {
  init {
    "a boolean setting is true or false, in any case and with spaces around it" {
      parseBoolean("SHOW_RESET_BUTTON", "true", false) shouldBe true
      parseBoolean("SHOW_RESET_BUTTON", " TRUE ", false) shouldBe true
      parseBoolean("SHOW_RESET_BUTTON", "False", true) shouldBe false
    }

    "an unset or blank boolean setting is its default" {
      parseBoolean("SHOW_RESET_BUTTON", null, true) shouldBe true
      parseBoolean("SHOW_RESET_BUTTON", "  ", false) shouldBe false
    }

    "any other boolean value is refused, naming the setting, rather than read as false" {
      listOf("1", "yes", "on", "t").forEach { value ->
        shouldThrow<IllegalArgumentException> { parseBoolean("SHOW_RESET_BUTTON", value, false) }.message.run {
          this shouldContain "SHOW_RESET_BUTTON is \"$value\""
          this shouldContain "true or false"
        }
      }
    }

    "a number setting is a whole number in its range, spaces around it allowed" {
      parseInt("CLIENT_VERSION_CHECK_INTERVAL_SECS", "60", 60, 10..3600) shouldBe 60
      parseInt("CLIENT_VERSION_CHECK_INTERVAL_SECS", " 90 ", 60, 10..3600) shouldBe 90
      parseInt("CLIENT_VERSION_CHECK_INTERVAL_SECS", "10", 60, 10..3600) shouldBe 10
      parseInt("CLIENT_VERSION_CHECK_INTERVAL_SECS", "3600", 60, 10..3600) shouldBe 3600
    }

    "an unset or blank number setting is its default" {
      parseInt("CLIENT_VERSION_CHECK_INTERVAL_SECS", null, 60, 10..3600) shouldBe 60
      parseInt("CLIENT_VERSION_CHECK_INTERVAL_SECS", "", 60, 10..3600) shouldBe 60
    }

    "a number setting that is not a whole number, or is out of range, is refused, naming the setting and the range" {
      listOf("60s", "1.5", "0", "-5", "9", "3601", "99999999999").forEach { value ->
        shouldThrow<IllegalArgumentException> {
          parseInt("CLIENT_VERSION_CHECK_INTERVAL_SECS", value, 60, 10..3600)
        }.message.run {
          this shouldContain "CLIENT_VERSION_CHECK_INTERVAL_SECS is \"$value\""
          this shouldContain "from 10 to 3600"
        }
      }
    }

    "the version-check ranges keep a page from polling faster than every ten seconds" {
      ServerVersionCheck.VERSION_CHECK_INTERVAL_SECS.first shouldBe 10
      val reloadWarningRange = ServerVersionCheck.RELOAD_WARNING_INTERVAL_SECS
      parseInt("CLIENT_RELOAD_WARNING_INTERVAL_SECS", "0", 60, reloadWarningRange) shouldBe 0
    }
  }
}
