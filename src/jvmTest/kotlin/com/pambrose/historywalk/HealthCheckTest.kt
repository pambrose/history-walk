package com.pambrose.historywalk

import com.pambrose.historywalk.dbms.HealthCheck
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.sql.SQLException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource
import kotlin.time.measureTime

/**
 * The probe behind `/health`, which anyone can call: it answers whether the database does, gives up at its timeout,
 * and, however many calls arrive, runs one query at a time and reuses a finished one's answer for a while, so calls
 * cannot pile up blocked threads or take the walk's connections.
 */
class HealthCheckTest : StringSpec() {
  init {
    "a probe that returns is true, and one that throws is false" {
      HealthCheck(1.seconds, 5.seconds) {}.answers() shouldBe true
      HealthCheck(1.seconds, 5.seconds) { throw SQLException("Connection refused") }.answers() shouldBe false
    }

    "a call gives up at the timeout, without waiting for a probe that hangs" {
      val release = CountDownLatch(1)
      val check = HealthCheck(100.milliseconds, 5.seconds) { release.await(10, TimeUnit.SECONDS) }
      var answered = true
      val elapsed = measureTime { answered = check.answers() }
      release.countDown()
      answered shouldBe false
      elapsed.inWholeMilliseconds.toInt() shouldBeLessThan 2_000
    }

    "calls that arrive while a probe hangs wait on it, rather than starting probes of their own" {
      val probes = AtomicInteger()
      val release = CountDownLatch(1)
      val check =
        HealthCheck(200.milliseconds, 5.seconds) {
          probes.incrementAndGet()
          release.await(10, TimeUnit.SECONDS)
        }
      val answers = coroutineScope { List(20) { async { check.answers() } }.awaitAll() }
      // A later call, after every one of those has given up, still finds the one probe running
      val later = check.answers()
      release.countDown()
      answers.toSet() shouldBe setOf(false)
      later shouldBe false
      probes.get() shouldBe 1
    }

    "a finished probe's answer is reused until it is older than the maximum age, then probed again" {
      val probes = AtomicInteger()
      var databaseUp = true
      val clock = TestTimeSource()
      val check =
        HealthCheck(1.seconds, 5.seconds, clock) {
          probes.incrementAndGet()
          if (!databaseUp) throw SQLException("Connection refused")
        }

      check.answers() shouldBe true
      databaseUp = false
      clock += 4.seconds
      check.answers() shouldBe true
      probes.get() shouldBe 1

      clock += 2.seconds
      check.answers() shouldBe false
      probes.get() shouldBe 2

      // A failure is reused too, until it is old enough to probe again
      databaseUp = true
      check.answers() shouldBe false
      clock += 6.seconds
      check.answers() shouldBe true
      probes.get() shouldBe 3
    }
  }
}
