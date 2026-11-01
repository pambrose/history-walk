package com.pambrose.historywalk.auth

import com.pambrose.historywalk.auth.OAuthState.oauthStateCookie
import com.pambrose.historywalk.auth.OAuthState.stateMatchesCookie
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class OAuthStateTest : StringSpec() {
  init {
    "a fresh state verifies" {
      val m = SignedNonceManager(ByteArray(32) { 1 })
      runBlocking { m.verifyNonce(m.newNonce()) } shouldBe true
    }

    "an expired state does not" {
      var now = Instant.fromEpochSeconds(1_000)
      val m = SignedNonceManager(ByteArray(32) { 1 }, clock = { now })
      val s = runBlocking { m.newNonce() }
      now += 11.minutes
      runBlocking { m.verifyNonce(s) } shouldBe false
    }

    "a pushed-back expiry breaks the signature" {
      val now = Instant.fromEpochSeconds(1_000)
      val m = SignedNonceManager(ByteArray(32) { 1 }, clock = { now })
      val parts = runBlocking { m.newNonce() }.split('.').toMutableList()
      parts[1] = (parts[1].toLong() + 3_600).toString()
      runBlocking { m.verifyNonce(parts.joinToString(".")) } shouldBe false
    }

    "another key's state does not verify" {
      val issuer = SignedNonceManager(ByteArray(32) { 1 })
      val other = SignedNonceManager(ByteArray(32) { 2 })
      runBlocking { other.verifyNonce(issuer.newNonce()) } shouldBe false
    }

    "malformed states do not verify" {
      val m = SignedNonceManager(ByteArray(32) { 1 })
      listOf("", "a.b", "a.b.c.d", "a.notanumber.c").forEach { runBlocking { m.verifyNonce(it) } shouldBe false }
    }

    "PKCE matches RFC 7636 appendix B" {
      Pkce.codeChallenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk") shouldBe
        "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"
    }

    "a verifier is 43 URL-safe characters" {
      Pkce.codeVerifier("state", ByteArray(32)).let {
        it.length shouldBe 43
        it shouldMatch Regex("[A-Za-z0-9_-]+")
      }
    }

    "a state matches only its own cookie" {
      stateMatchesCookie("abc", "abc") shouldBe true
      stateMatchesCookie("abc", "abd") shouldBe false
      stateMatchesCookie("abc", null) shouldBe false
      stateMatchesCookie("", "") shouldBe false
    }

    "the state cookie survives the redirect back from Google" {
      oauthStateCookie("s", secure = true).let {
        it.name shouldBe "__Host-oauth_state"
        it.extensions["SameSite"] shouldBe "Lax"
        it.httpOnly shouldBe true
        it.secure shouldBe true
        it.maxAge shouldBe 600
      }
    }
  }
}
