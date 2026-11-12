package com.pambrose.historywalk.dbms

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Whether [probe] returns, rather than throws, within [timeout], for `/health`, which anyone can call. Two things keep
 * those calls from costing the walk anything, however many arrive:
 * - Only one probe runs at a time. A call that finds one running waits on it, rather than starting another.
 * - A finished probe's result is reused for [maxAge], so at most one probe runs per [maxAge].
 *
 * JDBC cannot be interrupted, so a probe runs on the IO dispatcher, apart from the calls: with the database down,
 * Hikari waits up to its connection timeout (30 seconds) for a connection, and the calls are answered false at
 * [timeout] while that one wait finishes on its own. Until it does, no other probe starts.
 */
internal class HealthCheck(
  private val timeout: Duration,
  private val maxAge: Duration,
  private val timeSource: TimeSource = TimeSource.Monotonic,
  private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
  private val probe: () -> Unit,
) {
  private val lock = Mutex()
  private var running: Deferred<Boolean>? = null
  private var lastResult = false
  private var lastFinished: TimeMark? = null

  suspend fun answers(): Boolean {
    val check =
      lock.withLock {
        lastFinished?.takeIf { it.elapsedNow() < maxAge }?.also { return lastResult }
        running?.takeIf { it.isActive } ?: start().also { running = it }
      }
    return withTimeoutOrNull(timeout) { check.await() } ?: false
  }

  // Called with the lock held; the probe records its result once it is done, however long that takes
  private fun start(): Deferred<Boolean> =
    scope.async {
      val result = runCatching(probe).isSuccess
      lock.withLock {
        lastResult = result
        lastFinished = timeSource.markNow()
      }
      result
    }
}
