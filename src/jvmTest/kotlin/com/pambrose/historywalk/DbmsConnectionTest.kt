package com.pambrose.historywalk

import com.pambrose.historywalk.dbms.Dbms
import com.pambrose.historywalk.dbms.Dbms.unencryptedUnlessAsked
import com.zaxxer.hikari.HikariConfig
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import org.postgresql.Driver

class DbmsConnectionTest : StringSpec() {
  // The settings pgjdbc connects with: Hikari hands the driver the URL and its data source properties, and the URL's
  // parameters win over the properties.
  private fun sslModeFor(jdbcUrl: String): String? {
    val driverProperties = HikariConfig().apply { unencryptedUnlessAsked() }.dataSourceProperties
    return Driver.parseURL(jdbcUrl, driverProperties)?.getProperty("sslmode")
  }

  init {
    "a URL that sets no sslmode connects unencrypted, not with pgjdbc's prefer" {
      sslModeFor("jdbc:postgresql://db.example.com:5432/walk") shouldBe "disable"
      sslModeFor("jdbc:postgresql://db.example.com:5432/walk?application_name=walk") shouldBe "disable"
    }

    "a URL's own sslmode wins" {
      sslModeFor("jdbc:postgresql://db.example.com:5432/walk?sslmode=require") shouldBe "require"
      sslModeFor("jdbc:postgresql://db.example.com:5432/walk?application_name=walk&sslmode=verify-full") shouldBe
        "verify-full"
    }

    "a pgjdbc URL passes, with the spaces around it dropped" {
      val url = "jdbc:postgresql://localhost:5432/jermain?sslmode=require"
      Dbms.requirePgjdbcUrl(url) shouldBe url
      Dbms.requirePgjdbcUrl(" $url\n") shouldBe url
      Dbms.requirePgjdbcUrl("jdbc:postgresql:jermain") shouldBe "jdbc:postgresql:jermain"
    }

    "a pgjdbc-ng jdbc:pgsql: URL, in any case, is refused, naming the fix but not the URL, which may hold a password" {
      listOf(
        " jdbc:pgsql://localhost:5432/jermain?password=xyzzy",
        "JDBC:PGSQL://localhost:5432/jermain?password=xyzzy",
      ).forEach { url ->
        shouldThrow<IllegalArgumentException> { Dbms.requirePgjdbcUrl(url) }.message.orEmpty().run {
          this shouldStartWith "DBMS_URL starts with jdbc:pgsql:"
          this shouldNotContain "xyzzy"
          this shouldNotContain "jermain"
        }
      }
    }

    "any other URL is refused, without repeating it" {
      listOf(
        "postgres://walker:xyzzy@db.example.com:5432/jermain",
        "jdbc:postgres://walker:xyzzy@db.example.com:5432/jermain",
        "JDBC:POSTGRESQL://db.example.com:5432/jermain?password=xyzzy",
        "db.example.com:5432/jermain?password=xyzzy",
        "",
      ).forEach { url ->
        shouldThrow<IllegalArgumentException> { Dbms.requirePgjdbcUrl(url) }.message.orEmpty().run {
          this shouldStartWith "DBMS_URL must start with jdbc:postgresql:"
          this shouldNotContain "xyzzy"
          this shouldNotContain "walker"
          this shouldNotContain "example"
        }
      }
    }
  }
}
