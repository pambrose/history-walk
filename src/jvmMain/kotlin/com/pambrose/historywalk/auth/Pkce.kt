package com.pambrose.historywalk.auth

import java.security.MessageDigest
import java.util.Base64

/**
 * PKCE (RFC 7636) for Google sign-in, without storing anything on the server. A sign-in's verifier is an HMAC, under a
 * server key, of the random secret in the state cookie of the browser that started it ([OAuthState]). The callback,
 * which brings that cookie back, can work the verifier out again. Someone holding only the callback URL cannot, nor
 * get this server to: the secret is not in the URL, nor derived from anything that is. Each sign-in has its own
 * secret, so its own verifier.
 */
object Pkce {
  /** The verifier of the sign-in whose state cookie holds [secret]: 43 URL-safe characters, as RFC 7636 asks. */
  fun codeVerifier(
    secret: String,
    key: ByteArray,
  ): String = hmacSha256Base64Url(key, secret)

  /** The S256 challenge the authorize request carries for [verifier]. */
  fun codeChallenge(verifier: String): String =
    Base64
      .getUrlEncoder()
      .withoutPadding()
      .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
}
