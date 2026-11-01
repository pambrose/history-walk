package com.pambrose.historywalk.auth

import java.security.MessageDigest
import java.util.Base64

/**
 * PKCE (RFC 7636) for Google sign-in, without storing anything. A sign-in's verifier is an HMAC of its state under a
 * server key, so the callback, which brings the state back, can work the verifier out again, and no one without the
 * key can. The state is signed and good for one callback ([SignedNonceManager], [OAuthState]), so each sign-in has its
 * own verifier.
 */
object Pkce {
  /** The verifier of the sign-in whose state is [state]: 43 URL-safe characters, as RFC 7636 asks. */
  fun codeVerifier(
    state: String,
    key: ByteArray,
  ): String = hmacSha256Base64Url(key, state)

  /** The S256 challenge the authorize request carries for [verifier]. */
  fun codeChallenge(verifier: String): String =
    Base64
      .getUrlEncoder()
      .withoutPadding()
      .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
}
