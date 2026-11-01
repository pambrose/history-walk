package com.pambrose.historywalk

import com.pambrose.historywalk.SignInError.Companion.refusalEmailOrNull
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class SignInErrorTest : StringSpec() {
  init {
    "normal addresses pass" {
      refusalEmailOrNull("jsmith@gmail.com") shouldBe "jsmith@gmail.com"
      refusalEmailOrNull("a+b@x.org") shouldBe "a+b@x.org"
    }
    "text with spaces is dropped" { refusalEmailOrNull("Your account is locked call 555-0100") shouldBe null }
    "a missing at sign is dropped" { refusalEmailOrNull("jsmith.gmail.com") shouldBe null }
    "an over-long value is dropped" { refusalEmailOrNull("a".repeat(250) + "@x.org") shouldBe null }
    "a newline is dropped" { refusalEmailOrNull("a@x.org\nCall 555") shouldBe null }
    "a control character is dropped" { refusalEmailOrNull("a\u0000b@x.org") shouldBe null }
    "null stays null" { refusalEmailOrNull(null) shouldBe null }
  }
}
