package com.github.pambrose

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class UserDisplayTest : StringSpec() {
  init {
    "two words give two initials" { initialsOf("Maya Smith", "27msmith@athenian.org") shouldBe "MS" }
    "first and last of several words" { initialsOf("Maya  Anne Smith ", "x@athenian.org") shouldBe "MS" }
    "one word gives one initial" { initialsOf("Maya", "x@athenian.org") shouldBe "M" }
    "a blank name falls back to the email" { initialsOf(" ", "27msmith@athenian.org") shouldBe "2" }
    "initials are uppercase" { initialsOf("maya smith", "x@athenian.org") shouldBe "MS" }
  }
}
