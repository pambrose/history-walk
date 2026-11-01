package com.pambrose.historywalk

import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Instant

/** How an open page is told that its bundle is out of date. */
enum class AlertLevel {
  NONE,
  DIALOG,
  RELOAD,
}

/** A toast shown to the user; the colors are names of KVision's `BsColor` and `BsBgColor`. */
@Serializable
data class ToastConfig(
  val color: String? = null,
  val bgColor: String? = null,
  val autohide: Boolean = true,
  val duration: Duration? = null,
)

/** A dialog shown to the user; [size] names a KVision `ModalSize`. */
@Serializable
data class DialogConfig(
  val rich: Boolean = false,
  val centered: Boolean = false,
  val size: String? = null,
)

/**
 * What a page sends with each version check: the versions its bundle was built with (BuildConfig), and the time the
 * server first warned it, which the server set and the page sends back.
 */
@Serializable
data class VersionCheckInfo(
  val version: String,
  val apiVersion: String,
  val reloadWarningInstant: Instant?,
)

/**
 * The server's answer: whether and how to tell the user, the warning time to send back next time, and how long to
 * wait before the next check.
 */
@Serializable
data class VersionCheckResponse(
  val alertLevel: AlertLevel,
  val message: String = "",
  val title: String? = null,
  val toastConfig: ToastConfig? = null,
  val dialogConfig: DialogConfig? = null,
  val reloadWarningInstant: Instant? = null,
  val versionCheckInterval: Duration? = null,
)
