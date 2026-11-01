package com.pambrose.historywalk.auth

import io.ktor.util.NonceManager
import io.ktor.util.generateNonceBlocking
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Issues and checks the OAuth `state` without storing it. A state is `random.expiry.signature`: a random nonce, the
 * second it stops being good, and an HMAC-SHA256 of the two under [key]. Only this server can sign one, and pushing
 * its expiry back breaks the signature.
 *
 * It replaces a store of issued states. Anyone can start a sign-in without finishing it, so that store had to be
 * bounded, and a flood of unfinished sign-ins filled it, failing every sign-in begun after. Nothing here fills.
 *
 * Verifying a state does not use it up. One-time use is the state cookie's job ([OAuthState]): the cookie ties the
 * state to the browser that started the sign-in, the callback clears it, and Google's authorization code works once.
 *
 * @param key the signing key, [SessionConfig.oauthStateKey] in the server.
 * @param ttl how long a state is good for, the trip to Google and back.
 * @param clock the time source, injectable for testing.
 */
class SignedNonceManager(
  private val key: ByteArray,
  private val ttl: Duration = 10.minutes,
  private val clock: () -> Instant = { Clock.System.now() },
) : NonceManager {
  override suspend fun newNonce(): String {
    val unsigned = "${generateNonceBlocking()}.${(clock() + ttl).epochSeconds}"
    return "$unsigned.${sign(unsigned)}"
  }

  override suspend fun verifyNonce(nonce: String): Boolean {
    val parts = nonce.split('.')
    if (parts.size != 3) return false
    val (random, expiry, signature) = parts
    val expiresAt = expiry.toLongOrNull() ?: return false
    val signed = MessageDigest.isEqual(sign("$random.$expiry").toByteArray(), signature.toByteArray())
    return signed && clock() < Instant.fromEpochSeconds(expiresAt)
  }

  // URL-safe and free of dots, as the state travels in a URL and a cookie
  private fun sign(unsigned: String): String = hmacSha256Base64Url(key, unsigned)

  companion object {
    /** The server's instance, keyed from `SESSION_SECRET`. */
    val instance: SignedNonceManager by lazy { SignedNonceManager(SessionConfig.oauthStateKey) }
  }
}

private const val HMAC_SHA256 = "HmacSHA256"

/** The HMAC-SHA256 of [text] under [key], in base64url without padding, which signs a state and derives a verifier. */
internal fun hmacSha256Base64Url(
  key: ByteArray,
  text: String,
): String =
  Mac.getInstance(HMAC_SHA256).run {
    init(SecretKeySpec(key, HMAC_SHA256))
    Base64.getUrlEncoder().withoutPadding().encodeToString(doFinal(text.toByteArray()))
  }
