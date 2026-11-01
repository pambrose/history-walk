package com.pambrose.historywalk.dbms

import io.ktor.http.decodeURLPart
import java.net.URI
import java.net.URISyntaxException

// The connection settings Heroku hands the app as DATABASE_URL, postgres://user:password@host:port/dbname.
// Both apps ship their logs to Papertrail, so nothing built from the URL may carry the password into a log line:
// description names the database and the user only, and toString returns it in case the whole value gets logged.
internal class HerokuDatabaseUrl(
  val jdbcUrl: String,
  val username: String,
  val password: String,
  val description: String,
) {
  override fun toString() = description

  companion object {
    private const val DEFAULT_PORT = 5432

    fun parse(databaseUrl: String): HerokuDatabaseUrl {
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
      // Any query string is dropped: its parameters are libpq's, and the pgjdbc-ng URL below sets its own SSL mode.
      val dbName = uri.rawPath.orEmpty().removePrefix("/")
      require(dbName.isNotEmpty()) { "DATABASE_URL has no database name" }
      val userInfo = requireNotNull(uri.rawUserInfo) { "DATABASE_URL has no user name or password" }

      // The user info is split before it is decoded, so a percent-encoded : in the user name stays in the user name,
      // and at the first :, since a password may contain one. decodeURLPart, unlike URLDecoder, leaves a + as a +.
      val username = userInfo.substringBefore(":").decodeURLPart()
      val password = userInfo.substringAfter(":", "").decodeURLPart()

      return HerokuDatabaseUrl(
        jdbcUrl = "jdbc:pgsql://$host:$port/$dbName?ssl.mode=require",
        username = username,
        password = password,
        description = "$host:$port/$dbName as $username",
      )
    }
  }
}
