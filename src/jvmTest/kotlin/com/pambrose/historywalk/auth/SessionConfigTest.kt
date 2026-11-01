package com.pambrose.historywalk.auth

import com.pambrose.historywalk.auth.SessionConfig.DEV_ONLY_SECRET
import com.pambrose.historywalk.auth.SessionConfig.deriveKey
import com.pambrose.historywalk.auth.SessionConfig.isLocalCallback
import com.pambrose.historywalk.auth.SessionConfig.resolveSecret
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class SessionConfigTest : StringSpec() {
  init {
    "localhost, 127.0.0.1 and ::1 callbacks are local" {
      listOf(
        "http://localhost:8080/auth/callback",
        "http://127.0.0.1:3000/auth/callback",
        "http://[::1]:8080/auth/callback",
      ).forEach { isLocalCallback(it) shouldBe true }
    }

    "a Heroku callback is not local" {
      isLocalCallback("https://history-walk.herokuapp.com/auth/callback") shouldBe false
    }

    "no callback is local, a blank one is not" {
      isLocalCallback(null) shouldBe true
      isLocalCallback("") shouldBe false
    }

    "production requires a secret" {
      shouldThrow<IllegalArgumentException> { resolveSecret("", "https://x.herokuapp.com/auth/callback") }
    }

    "production rejects a short secret" {
      shouldThrow<IllegalArgumentException> { resolveSecret("short", "https://x.herokuapp.com/auth/callback") }
    }

    "production uses the trimmed secret" {
      resolveSecret("  0123456789abcdef0123  ", "https://x.herokuapp.com/auth/callback") shouldBe
        "0123456789abcdef0123"
    }

    "local falls back to the dev secret" {
      resolveSecret("", "http://localhost:8080/auth/callback") shouldBe DEV_ONLY_SECRET
      resolveSecret("", null) shouldBe DEV_ONLY_SECRET
    }

    "derived keys are 32 bytes and differ by label" {
      deriveKey("s", "enc").size shouldBe 32
      deriveKey("s", "enc").contentEquals(deriveKey("s", "mac")) shouldBe false
    }

    "secure cookie names get the __Host- prefix" {
      hostCookieName("HistoryWalkAuth", true) shouldBe "__Host-HistoryWalkAuth"
      hostCookieName("HistoryWalkAuth", false) shouldBe "HistoryWalkAuth"
    }
  }
}
