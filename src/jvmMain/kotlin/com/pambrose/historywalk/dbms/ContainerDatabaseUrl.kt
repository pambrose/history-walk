package com.pambrose.historywalk.dbms

import io.ktor.http.decodeURLPart
import java.net.URI
import java.net.URISyntaxException

// The connection settings a container platform hands the app as DATABASE_URL,
// postgres://user:password@host:port/dbname (Railway's ${{Postgres.DATABASE_URL}}, for one). Logs may be shipped and
// kept elsewhere, so nothing built from the URL may carry the password into a log line: description names the database
// and the user only, and toString returns it in case the whole value gets logged.
internal class ContainerDatabaseUrl(
  val jdbcUrl: String,
  val username: String,
  val password: String,
  val description: String,
) {
  override fun toString() = description

  companion object {
    private const val DEFAULT_PORT = 5432

    // dbNameOverride (DBMS_NAME), when not blank, names the database in place of the URL's, on the same server with the
    // same user: Railway's ${{Postgres.DATABASE_URL}} names its default database, and each walk keeps its own.
    // queryPathOverride (DBMS_QUERY_PATH), when not blank, is the query string in place of the URL's, written as in a URL
    // (percent-encoded, with or without the leading ?).
    fun parse(
      databaseUrl: String,
      dbNameOverride: String = "",
      queryPathOverride: String = "",
    ): ContainerDatabaseUrl {
      // A URISyntaxException's message repeats the whole input, password included, so only its reason is passed
      // on, and the exception is not kept as the cause.
      val uri =
        try {
          URI(databaseUrl)
        } catch (e: URISyntaxException) {
          throw IllegalArgumentException("DATABASE_URL is not a valid URI: ${e.reason} at index ${e.index}")
        }

      // URI finds no host when the authority does not parse as user@host:port, most often because an @ in the
      // password was not percent-encoded.
      val host = requireNotNull(uri.host) { "DATABASE_URL has no host; an @ in the password must be percent-encoded" }
      val port = if (uri.port == -1) DEFAULT_PORT else uri.port
      val dbName = dbNameOverride.trim().ifEmpty { uri.rawPath.orEmpty().removePrefix("/") }
      require(dbName.isNotEmpty()) { "DATABASE_URL has no database name" }
      // The query string goes to pgjdbc still percent-encoded, since the driver decodes it. Its parameters are pgjdbc's,
      // which take libpq's names for the common ones (sslmode=require encrypts); the driver ignores a name it does not
      // know. Without an sslmode the connection is unencrypted (Dbms.unencryptedUnlessAsked), as suits a platform's
      // private network.
      val query = queryPathOverride.trim().removePrefix("?").ifEmpty { uri.rawQuery.orEmpty() }
      val userInfo = requireNotNull(uri.rawUserInfo) { "DATABASE_URL has no user name or password" }

      // The user info is split before it is decoded, so a percent-encoded : in the user name stays in the user name,
      // and at the first :, since a password may contain one. decodeURLPart, unlike URLDecoder, leaves a + as a +.
      val username = userInfo.substringBefore(":").decodeURLPart()
      val password = userInfo.substringAfter(":", "").decodeURLPart()

      // The description leaves the query string out, since pgjdbc would take a password in it.
      return ContainerDatabaseUrl(
        jdbcUrl = "jdbc:postgresql://$host:$port/$dbName" + if (query.isEmpty()) "" else "?$query",
        username = username,
        password = password,
        description = "$host:$port/$dbName as $username",
      )
    }
  }
}
