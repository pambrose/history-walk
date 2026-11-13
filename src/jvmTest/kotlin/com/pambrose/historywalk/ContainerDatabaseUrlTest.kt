package com.pambrose.historywalk

import com.pambrose.historywalk.dbms.ContainerDatabaseUrl
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith

/**
 * A platform's `DATABASE_URL` becomes a pgjdbc URL, a decoded user name, and a decoded password, with the URL's query
 * string kept percent-encoded, and `DBMS_NAME` and `DBMS_QUERY_PATH`, when not blank, in place of the URL's database
 * and query string. The description leaves out the password and the query string, and a URL that does not parse or
 * names no database is refused without echoing the password.
 */
class ContainerDatabaseUrlTest : StringSpec() {
  init {
    "a container platform's DATABASE_URL becomes a pgjdbc URL, a user name, and a password" {
      ContainerDatabaseUrl
        .parse("postgres://u1a2b3:p9f8e7d6@ec2-52-1-2-3.compute-1.amazonaws.com:5432/d4c5b6a7")
        .run {
          jdbcUrl shouldBe "jdbc:postgresql://ec2-52-1-2-3.compute-1.amazonaws.com:5432/d4c5b6a7"
          username shouldBe "u1a2b3"
          password shouldBe "p9f8e7d6"
        }
    }

    "the port defaults to 5432 when the URL has none" {
      ContainerDatabaseUrl.parse("postgres://user:secret@db.example.com/walk").jdbcUrl shouldBe
        "jdbc:postgresql://db.example.com:5432/walk"
    }

    "a password with a percent-encoded @ and a : is split at the first : and decoded" {
      ContainerDatabaseUrl.parse("postgres://user:p%40ss:wo%3Ard@db.example.com:6543/walk").run {
        username shouldBe "user"
        password shouldBe "p@ss:wo:rd"
        jdbcUrl shouldBe "jdbc:postgresql://db.example.com:6543/walk"
      }
    }

    "a percent-encoded user name is decoded" {
      ContainerDatabaseUrl.parse("postgres://walk%40admin:secret@db.example.com:5432/walk").username shouldBe
        "walk@admin"
    }

    "a + in the password stays a +, not a space" {
      ContainerDatabaseUrl.parse("postgres://user:a+b@db.example.com:5432/walk").password shouldBe "a+b"
    }

    "the URL's query string is added to the JDBC URL as it stands, still percent-encoded" {
      val url = "postgres://user:secret@db.example.com:5432/walk?sslmode=require&application_name=history%20walk"
      ContainerDatabaseUrl.parse(url).run {
        jdbcUrl shouldBe "jdbc:postgresql://db.example.com:5432/walk?sslmode=require&application_name=history%20walk"
        password shouldBe "secret"
      }
    }

    "a ? with nothing after it adds no query string" {
      ContainerDatabaseUrl.parse("postgres://user:secret@db.example.com:5432/walk?").jdbcUrl shouldBe
        "jdbc:postgresql://db.example.com:5432/walk"
    }

    "DBMS_QUERY_PATH replaces the URL's query string" {
      val url = "postgres://user:secret@db.example.com:5432/walk?sslmode=disable&application_name=walk"
      ContainerDatabaseUrl.parse(url, queryPathOverride = "sslmode=require").jdbcUrl shouldBe
        "jdbc:postgresql://db.example.com:5432/walk?sslmode=require"
    }

    "DBMS_QUERY_PATH supplies the query string a URL lacks, after DBMS_NAME's database" {
      val railway = "postgres://user:secret@db.example.com:5432/railway"
      ContainerDatabaseUrl
        .parse(railway, dbNameOverride = "jermain", queryPathOverride = "sslmode=require")
        .jdbcUrl shouldBe "jdbc:postgresql://db.example.com:5432/jermain?sslmode=require"
    }

    "a blank DBMS_QUERY_PATH leaves the URL's query string, and spaces and a leading ? around one are dropped" {
      val walk = "postgres://user:secret@db.example.com:5432/walk?sslmode=prefer"
      ContainerDatabaseUrl.parse(walk, queryPathOverride = "  ").jdbcUrl shouldBe
        "jdbc:postgresql://db.example.com:5432/walk?sslmode=prefer"
      ContainerDatabaseUrl.parse(walk, queryPathOverride = " ?sslmode=require ").jdbcUrl shouldBe
        "jdbc:postgresql://db.example.com:5432/walk?sslmode=require"
    }

    "the description leaves out the query string, which may hold a password" {
      val url = "postgres://user:secret@db.example.com:5432/walk?password=xyzzy"
      ContainerDatabaseUrl.parse(url).description shouldBe "db.example.com:5432/walk as user"
      ContainerDatabaseUrl.parse(url, queryPathOverride = "password=plugh").description shouldNotContain "plugh"
    }

    "the description names the database and the user but never the password" {
      val password = "p%40ss:word"
      ContainerDatabaseUrl.parse("postgres://user:$password@db.example.com:5432/walk").run {
        description shouldBe "db.example.com:5432/walk as user"
        description shouldNotContain "p@ss:word"
        description shouldNotContain password
        toString() shouldBe description
      }
    }

    "an unencoded @ in the password is refused without echoing the password" {
      shouldThrow<IllegalArgumentException> {
        ContainerDatabaseUrl.parse("postgres://user:p@xyzzy@db.example.com:5432/walk")
      }.run {
        message.orEmpty() shouldStartWith "DATABASE_URL has no host"
        message.orEmpty() shouldNotContain "xyzzy"
      }
    }

    "a URL that does not parse is refused without echoing the password or keeping the exception that holds it" {
      shouldThrow<IllegalArgumentException> {
        ContainerDatabaseUrl.parse("postgres://user:100%xyzzy@db.example.com:5432/walk")
      }.run {
        message.orEmpty() shouldStartWith "DATABASE_URL is not a valid URI"
        message.orEmpty() shouldNotContain "xyzzy"
        cause shouldBe null
      }
    }

    "DBMS_NAME replaces the URL's database, on the same server with the same user" {
      val railway = "postgres://user:secret@db.example.com:5432/railway"
      ContainerDatabaseUrl.parse(railway, dbNameOverride = "jermain").run {
        jdbcUrl shouldBe "jdbc:postgresql://db.example.com:5432/jermain"
        username shouldBe "user"
        password shouldBe "secret"
        description shouldBe "db.example.com:5432/jermain as user"
      }
    }

    "DBMS_NAME supplies the database a URL lacks" {
      val noDatabase = "postgres://user:secret@db.example.com:5432"
      ContainerDatabaseUrl.parse(noDatabase, dbNameOverride = "moses").jdbcUrl shouldBe
        "jdbc:postgresql://db.example.com:5432/moses"
    }

    "a blank DBMS_NAME leaves the URL's database, and spaces around one are dropped" {
      val walk = "postgres://user:secret@db.example.com:5432/walk"
      ContainerDatabaseUrl.parse(walk, dbNameOverride = "  ").jdbcUrl shouldBe
        "jdbc:postgresql://db.example.com:5432/walk"
      ContainerDatabaseUrl.parse(walk, dbNameOverride = " jermain ").jdbcUrl shouldBe
        "jdbc:postgresql://db.example.com:5432/jermain"
    }

    "a URL without a database name is refused" {
      shouldThrow<IllegalArgumentException> {
        ContainerDatabaseUrl.parse("postgres://user:secret@db.example.com:5432")
      }.message shouldBe "DATABASE_URL has no database name"
    }
  }
}
