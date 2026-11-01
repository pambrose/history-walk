package com.pambrose.historywalk

import com.pambrose.historywalk.dbms.HerokuDatabaseUrl
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith

class HerokuDatabaseUrlTest : StringSpec() {
  init {
    "a Heroku DATABASE_URL becomes a pgjdbc-ng URL, a user name, and a password" {
      HerokuDatabaseUrl
        .parse("postgres://u1a2b3:p9f8e7d6@ec2-52-1-2-3.compute-1.amazonaws.com:5432/d4c5b6a7")
        .run {
          jdbcUrl shouldBe "jdbc:pgsql://ec2-52-1-2-3.compute-1.amazonaws.com:5432/d4c5b6a7?ssl.mode=require"
          username shouldBe "u1a2b3"
          password shouldBe "p9f8e7d6"
        }
    }

    "the port defaults to 5432 when the URL has none" {
      HerokuDatabaseUrl.parse("postgres://user:secret@db.example.com/walk").jdbcUrl shouldBe
        "jdbc:pgsql://db.example.com:5432/walk?ssl.mode=require"
    }

    "a password with a percent-encoded @ and a : is split at the first : and decoded" {
      HerokuDatabaseUrl.parse("postgres://user:p%40ss:wo%3Ard@db.example.com:6543/walk").run {
        username shouldBe "user"
        password shouldBe "p@ss:wo:rd"
        jdbcUrl shouldBe "jdbc:pgsql://db.example.com:6543/walk?ssl.mode=require"
      }
    }

    "a percent-encoded user name is decoded" {
      HerokuDatabaseUrl.parse("postgres://walk%40admin:secret@db.example.com:5432/walk").username shouldBe
        "walk@admin"
    }

    "a + in the password stays a +, not a space" {
      HerokuDatabaseUrl.parse("postgres://user:a+b@db.example.com:5432/walk").password shouldBe "a+b"
    }

    "a query string on the URL is left out of the JDBC URL" {
      HerokuDatabaseUrl.parse("postgres://user:secret@db.example.com:5432/walk?sslmode=require").run {
        jdbcUrl shouldBe "jdbc:pgsql://db.example.com:5432/walk?ssl.mode=require"
        password shouldBe "secret"
      }
    }

    "the description names the database and the user but never the password" {
      val password = "p%40ss:word"
      HerokuDatabaseUrl.parse("postgres://user:$password@db.example.com:5432/walk").run {
        description shouldBe "db.example.com:5432/walk as user"
        description shouldNotContain "p@ss:word"
        description shouldNotContain password
        toString() shouldBe description
      }
    }

    "an unencoded @ in the password is refused without echoing the password" {
      shouldThrow<IllegalArgumentException> {
        HerokuDatabaseUrl.parse("postgres://user:p@xyzzy@db.example.com:5432/walk")
      }.run {
        message.orEmpty() shouldStartWith "DATABASE_URL has no host"
        message.orEmpty() shouldNotContain "xyzzy"
      }
    }

    "a URL that does not parse is refused without echoing the password or keeping the exception that holds it" {
      shouldThrow<IllegalArgumentException> {
        HerokuDatabaseUrl.parse("postgres://user:100%xyzzy@db.example.com:5432/walk")
      }.run {
        message.orEmpty() shouldStartWith "DATABASE_URL is not a valid URI"
        message.orEmpty() shouldNotContain "xyzzy"
        cause shouldBe null
      }
    }

    "a URL without a database name is refused" {
      shouldThrow<IllegalArgumentException> {
        HerokuDatabaseUrl.parse("postgres://user:secret@db.example.com:5432")
      }.message shouldBe "DATABASE_URL has no database name"
    }
  }
}
