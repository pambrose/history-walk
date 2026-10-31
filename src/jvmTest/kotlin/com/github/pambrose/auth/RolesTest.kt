package com.github.pambrose.auth

import com.github.pambrose.Role
import com.github.pambrose.auth.Roles.parseTeacherEmails
import com.github.pambrose.auth.Roles.roleFor
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class RolesTest : StringSpec() {
  init {
    "a listed teacher is a teacher" {
      roleFor("pambrose@athenian.org", setOf("pambrose@athenian.org")) shouldBe Role.TEACHER
    }
    "the list is checked first, even for a two-digit address" {
      roleFor("27lab@athenian.org", setOf("27lab@athenian.org")) shouldBe Role.TEACHER
    }
    "a two-digit address is a student" {
      roleFor("27msmith@athenian.org", emptySet()) shouldBe Role.STUDENT
    }
    "an unlisted non-digit address is refused" {
      roleFor("coach@athenian.org", setOf("pambrose@athenian.org")) shouldBe null
    }
    "one leading digit is not a student" {
      roleFor("2msmith@athenian.org", emptySet()) shouldBe null
    }
    "matching ignores case" {
      roleFor("PAmbrose@Athenian.org", setOf("pambrose@athenian.org")) shouldBe Role.TEACHER
    }
    "messy lists parse cleanly" {
      parseTeacherEmails(" PAmbrose@Athenian.org, ,jdoe@athenian.org,") shouldBe
        setOf("pambrose@athenian.org", "jdoe@athenian.org")
    }
    "a missing list is empty" {
      parseTeacherEmails(null) shouldBe emptySet()
    }
  }
}
