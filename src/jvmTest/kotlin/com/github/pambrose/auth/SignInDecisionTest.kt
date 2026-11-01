package com.github.pambrose.auth

import com.github.pambrose.Role
import com.github.pambrose.SignInError
import com.github.pambrose.SignInError.NOT_ALLOWED
import com.github.pambrose.SignInError.UNVERIFIED
import com.github.pambrose.SignInError.WRONG_DOMAIN
import com.github.pambrose.auth.SignInDecision.Admitted
import com.github.pambrose.auth.SignInDecision.Rejected
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

class SignInDecisionTest : StringSpec() {
  private val lenientJson = Json { ignoreUnknownKeys = true }
  private val teachers = setOf("pambrose@athenian.org")

  private fun info(
    email: String,
    hd: String? = "athenian.org",
    verified: Boolean = true,
    picture: String? = "https://lh3.googleusercontent.com/a/x",
  ) = GoogleUserInfo(id = "1", email = email, name = "N", picture = picture, verifiedEmail = verified, hd = hd)

  init {
    "a student is admitted as a student" {
      info("27msmith@athenian.org").signInDecision("athenian.org", teachers) shouldBe
        Admitted("27msmith@athenian.org", "N", "https://lh3.googleusercontent.com/a/x", Role.STUDENT)
    }
    "a listed teacher is admitted as a teacher" {
      (info("pambrose@athenian.org").signInDecision("athenian.org", teachers) as Admitted).role shouldBe Role.TEACHER
    }
    "an unlisted staff account is refused" {
      info("coach@athenian.org").signInDecision("athenian.org", teachers) shouldBe
        Rejected(NOT_ALLOWED, "coach@athenian.org")
    }
    "a personal Gmail is refused" {
      info("jsmith@gmail.com", hd = null).signInDecision("athenian.org", teachers) shouldBe
        Rejected(WRONG_DOMAIN, "jsmith@gmail.com")
    }
    "another school's Workspace is refused" {
      info("27x@other.org", hd = "other.org").signInDecision("athenian.org", teachers) shouldBe
        Rejected(WRONG_DOMAIN, "27x@other.org")
    }
    "an athenian.org address without hd is refused" {
      info("27msmith@athenian.org", hd = null).signInDecision("athenian.org", teachers) shouldBe
        Rejected(WRONG_DOMAIN, "27msmith@athenian.org")
    }
    "an unverified email is refused first" {
      info("jsmith@gmail.com", hd = null, verified = false).signInDecision("athenian.org", teachers) shouldBe
        Rejected(UNVERIFIED, "jsmith@gmail.com")
    }
    "a mixed-case address is admitted lowercased" {
      (info("PAmbrose@Athenian.org", hd = "Athenian.org").signInDecision("athenian.org", teachers) as Admitted)
        .email shouldBe "pambrose@athenian.org"
    }
    "an account with no photo is admitted" {
      (info("27msmith@athenian.org", picture = null).signInDecision("athenian.org", teachers) as Admitted)
        .picture shouldBe null
    }
    "the refusal redirect encodes the email" {
      Rejected(WRONG_DOMAIN, "a+b@gmail.com").redirectUrl() shouldBe "/?error=domain&email=a%2Bb%40gmail.com"
    }
    "the wrong-domain message names the account" {
      WRONG_DOMAIN.message("jsmith@gmail.com") shouldBe
        "jsmith@gmail.com isn't an athenian.org account. Please sign in with your school account."
    }
    "a look-alike suffix or a subdomain is refused" {
      info("27x@evilathenian.org", hd = "athenian.org").signInDecision("athenian.org", teachers) shouldBe
        Rejected(WRONG_DOMAIN, "27x@evilathenian.org")
      info("27x@sub.athenian.org", hd = "athenian.org").signInDecision("athenian.org", teachers) shouldBe
        Rejected(WRONG_DOMAIN, "27x@sub.athenian.org")
    }
    "userinfo without a name still decodes and is admitted" {
      val json = """{"id":"1","email":"27msmith@athenian.org","verified_email":true,"hd":"athenian.org","extra":1}"""
      val parsed = lenientJson.decodeFromString<GoogleUserInfo>(json)
      parsed.signInDecision("athenian.org", teachers) shouldBe Admitted("27msmith@athenian.org", "", null, Role.STUDENT)
    }
    "codes round-trip" {
      SignInError.entries.forEach { SignInError.fromCode(it.code) shouldBe it }
      SignInError.fromCode("nope") shouldBe null
    }
  }
}
