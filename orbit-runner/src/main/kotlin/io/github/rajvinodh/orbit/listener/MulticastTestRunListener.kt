package io.github.rajvinodh.orbit.listener

import com.android.ddmlib.testrunner.ITestRunListener
import com.android.ddmlib.testrunner.TestIdentifier
import com.google.common.base.Stopwatch
import java.util.concurrent.TimeUnit

/**
 * Fans out DDMLib test events to multiple delegates. Manages a single logical run across multiple
 * per-test runner.run() calls via multiRunStarted/multiRunEnded — individual testRunStarted/Ended
 * events are suppressed.
 */
internal class MulticastTestRunListener(private val delegates: List<ITestRunListener>) :
  ITestRunListener {

  private val stopwatch = Stopwatch.createUnstarted()
  private var started = false
  private var stopped = false

  fun multiRunStarted(runName: String, testCount: Int) {
    check(!started) { "multiRunStarted called twice" }
    started = true
    stopwatch.start()
    delegates.forEach { it.testRunStarted(runName, testCount) }
  }

  fun multiRunEnded() {
    check(started && !stopped) { "Invalid multicast state: started=$started stopped=$stopped" }
    stopped = true
    val elapsed = stopwatch.elapsed(TimeUnit.SECONDS)
    delegates.forEach { it.testRunEnded(elapsed, emptyMap()) }
  }

  override fun testStarted(test: TestIdentifier) = delegates.forEach { it.testStarted(test) }

  override fun testFailed(test: TestIdentifier, trace: String) =
    delegates.forEach { it.testFailed(test, trace) }

  override fun testAssumptionFailure(test: TestIdentifier, trace: String) =
    delegates.forEach { it.testAssumptionFailure(test, trace) }

  override fun testIgnored(test: TestIdentifier) = delegates.forEach { it.testIgnored(test) }

  override fun testEnded(test: TestIdentifier, metrics: Map<String, String>) =
    delegates.forEach { it.testEnded(test, metrics) }

  override fun testRunFailed(errorMessage: String) =
    delegates.forEach { it.testRunFailed(errorMessage) }

  // Suppressed — session lifecycle handled by multiRunStarted/Ended
  override fun testRunStarted(runName: String, testCount: Int) {}

  override fun testRunStopped(elapsedTime: Long) {}

  override fun testRunEnded(elapsedTime: Long, runMetrics: Map<String, String>) {}
}
