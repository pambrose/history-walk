package com.pambrose.historywalk.auth

import com.pambrose.historywalk.auth.OAuthState.newSecret
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldMatch

class PkceTest : StringSpec() {
  private val key = ByteArray(32) { 3 }

  init {
    "the challenge matches RFC 7636 appendix B" {
      Pkce.codeChallenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk") shouldBe
        "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"
    }

    "a verifier is 43 URL-safe characters" {
      Pkce.codeVerifier(newSecret(), key).let {
        it.length shouldBe 43
        it shouldMatch Regex("[A-Za-z0-9_-]+")
      }
    }

    "the callback works out the same verifier from the same secret" {
      val secret = newSecret()
      Pkce.codeVerifier(secret, key) shouldBe Pkce.codeVerifier(secret, key)
    }

    "each sign-in's secret gives its own verifier" {
      Pkce.codeVerifier(newSecret(), key) shouldNotBe Pkce.codeVerifier(newSecret(), key)
    }

    "the verifier needs the server's key as well as the secret" {
      val secret = newSecret()
      Pkce.codeVerifier(secret, key) shouldNotBe Pkce.codeVerifier(secret, ByteArray(32) { 4 })
      Pkce.codeVerifier(secret, key) shouldNotBe secret
    }
  }
}
