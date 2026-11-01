package com.pambrose.historywalk.auth

import com.pambrose.historywalk.EnvVar
import com.pambrose.historywalk.School
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.sessions.SessionTransportTransformerEncrypt
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom

/** The school whose Google accounts may sign in, and its teachers. */
object SchoolConfig {
  val emailDomain: String by lazy {
    EnvVar.SCHOOL_EMAIL_DOMAIN
    .getEnv(School.DEFAULT_DOMAIN)
    .trim()
    .lowercase()
  }
  val teacherEmails: Set<String> by lazy { Roles.parseTeacherEmails(EnvVar.TEACHER_EMAILS.getEnvOrNull()) }
}

/** Google OAuth 2.0 credentials and callback URL. */
object OAuthConfig {
  val googleClientId: String by lazy { EnvVar.GOOGLE_CLIENT_ID.getRequiredEnv() }
  val googleClientSecret: String by lazy { EnvVar.GOOGLE_CLIENT_SECRET.getRequiredEnv() }
  val callbackUrl: String by lazy { EnvVar.OAUTH_CALLBACK_URL.getRequiredEnv() }
}

/**
 * Keys for the encrypted session cookie and the OAuth state, all derived from `SESSION_SECRET` via SHA-256 with a
 * label for domain separation. A server whose callback is not on a loopback host (production) requires a secret of at
 * least [MIN_SECRET_LENGTH] chars; a local one falls back to a DEV-ONLY secret with a warning.
 */
object SessionConfig {
  private val logger = KotlinLogging.logger {}

  const val MIN_SECRET_LENGTH = 16

  // DEV-ONLY fixed secret so local development works without SESSION_SECRET. NEVER use in production.
  const val DEV_ONLY_SECRET = "dev-only-insecure-session-secret-change-me"

  // AES/CBC requires a 16-byte (block-size) IV. Ktor 3.6.1 already uses 16 bytes; we keep the explicit generator so
  // the IV size does not depend on the library default.
  private const val AES_IV_LENGTH = 16

  private val LOOPBACK_HOSTS = setOf("localhost", "127.0.0.1", "::1")

  private val secureRandom = SecureRandom()

  // An unset callback counts as local so tests and local development need no environment; a deployed server still
  // fails at startup because verifyAuthConfig() requires OAUTH_CALLBACK_URL.
  val isProduction: Boolean by lazy { !isLocalCallback(EnvVar.OAUTH_CALLBACK_URL.getEnvOrNull()) }

  private val secret: String by lazy {
    resolveSecret(EnvVar.SESSION_SECRET.getEnv(""), EnvVar.OAUTH_CALLBACK_URL.getEnvOrNull())
  }

  val encryptionKey: ByteArray by lazy { deriveKey(secret, "enc") }
  val signKey: ByteArray by lazy { deriveKey(secret, "mac") }
  val oauthStateKey: ByteArray by lazy { deriveKey(secret, "state") }
  val pkceKey: ByteArray by lazy { deriveKey(secret, "pkce") }

  fun encryptTransformer(): SessionTransportTransformerEncrypt =
    SessionTransportTransformerEncrypt(
      encryptionKey,
      signKey,
      ivGenerator = { ByteArray(AES_IV_LENGTH).also { iv -> secureRandom.nextBytes(iv) } },
    )

  /** Whether sign-in comes back to this machine: no callback configured, or a loopback host. Blank is not local. */
  internal fun isLocalCallback(callbackUrl: String?): Boolean =
    callbackUrl == null ||
      runCatching { URI(callbackUrl).host?.removeSurrounding("[", "]") }.getOrNull() in LOOPBACK_HOSTS

  /** The trimmed [configured] secret; a non-local callback requires one of at least [MIN_SECRET_LENGTH] chars. */
  internal fun resolveSecret(
    configured: String,
    callbackUrl: String?,
  ): String {
    val trimmed = configured.trim()
    return if (!isLocalCallback(callbackUrl)) {
      require(trimmed.isNotBlank()) {
        "SESSION_SECRET must be set for a server reached at $callbackUrl. Generate one with: openssl rand -hex 32"
      }
      require(trimmed.length >= MIN_SECRET_LENGTH) {
        "SESSION_SECRET is too weak (min $MIN_SECRET_LENGTH chars). Generate one with: openssl rand -hex 32"
      }
      trimmed
    } else {
      trimmed.ifBlank {
      logger.warn { "SESSION_SECRET is not set; using an insecure DEV-ONLY session secret. Never use in production." }
      DEV_ONLY_SECRET
    }
    }
  }

  /** A 32-byte key from [secret] and a domain-separation [label]. */
  internal fun deriveKey(
    secret: String,
    label: String,
  ): ByteArray = MessageDigest.getInstance("SHA-256").digest("$secret|$label".toByteArray())
}

/** The cookie name, with the `__Host-` prefix where the cookie is [secure] (browsers accept it only when Secure). */
internal fun hostCookieName(
  name: String,
  secure: Boolean,
): String = if (secure) "__Host-$name" else name

/** Fails startup if any required auth setting is missing. */
fun verifyAuthConfig() {
  OAuthConfig.googleClientId
  OAuthConfig.googleClientSecret
  OAuthConfig.callbackUrl
  SessionConfig.encryptionKey
}
