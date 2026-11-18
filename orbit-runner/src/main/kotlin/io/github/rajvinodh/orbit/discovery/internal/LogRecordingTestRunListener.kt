package io.github.rajvinodh.orbit.discovery.internal

import com.android.ddmlib.testrunner.ITestRunListener
import com.android.ddmlib.testrunner.TestIdentifier

/**
 * Listens to a `log=true` dry-run instrumentation call and records the full test suite structure.
 * Internal to the discovery package — callers receive a typed DiscoveryResult, never this class.
 */
internal class LogRecordingTestRunListener : ITestRunListener {

  companion object {
    private val PARAM_SUFFIX = "\\[.*\\]$".toRegex()

    fun stripParams(test: TestIdentifier): TestIdentifier {
      val className = test.className.replace(PARAM_SUFFIX, "")
      return TestIdentifier(className, test.testName)
    }
  }

  private val _activeTests = LinkedHashSet<TestIdentifier>()
  private val _ignoredTests = LinkedHashSet<TestIdentifier>()

  // Per-test metadata delivered via setCustomTestDetails instrumentation key
  private val annotationMap = HashMap<String, List<String>>()
  private val clearAppDataActions = HashMap<String, String>() // TestIdentifier.toString() → action
  private val testToExternalId = HashMap<String, String>() // TestIdentifier.toString() → externalId
  private val seenExternalIds =
    HashMap<String, String>() // externalId → TestIdentifier.toString() (dup detection)

  private var _runName: String? = null
  private var _isTestRunFailed: Boolean? = null

  fun activeTests(): List<TestIdentifier> = ArrayList(_activeTests)

  fun ignoredTests(): List<TestIdentifier> = ArrayList(_ignoredTests)

  fun runName(): String? = _runName

  fun isTestRunFailed(): Boolean? = _isTestRunFailed

  fun annotationsFor(test: TestIdentifier): List<String> =
    annotationMap[test.testName] ?: emptyList()

  fun clearAppDataActionFor(test: TestIdentifier): String? = clearAppDataActions[test.toString()]

  fun externalIdFor(test: TestIdentifier): String? = testToExternalId[test.toString()]

  override fun testRunStarted(runName: String, testCount: Int) {
    _runName = runName
    _isTestRunFailed = false
  }

  override fun testStarted(test: TestIdentifier) {
    _activeTests.add(stripParams(test))
  }

  override fun testIgnored(test: TestIdentifier) {
    val stripped = stripParams(test)
    _activeTests.remove(stripped)
    _ignoredTests.add(stripped)
  }

  /**
   * Instrumentation delivers per-test metadata as a special testEnded metric. Keys mirror the Orbit
   * convention so existing test annotations keep working.
   */
  override fun testEnded(test: TestIdentifier, testMetrics: Map<String, String>) {
    val annotations = testMetrics["annotations"]?.split(",")?.map { it.trim() } ?: emptyList()
    val clearAction = testMetrics["clearAppDataAction"]
    val externalId = testMetrics["externalId"]

    if (annotations.isNotEmpty()) annotationMap[test.testName] = annotations
    if (!clearAction.isNullOrEmpty()) clearAppDataActions[test.toString()] = clearAction
    if (!externalId.isNullOrEmpty()) {
      val existingTest = seenExternalIds[externalId]
      if (existingTest != null) {
        throw IllegalStateException("Duplicate externalId '$externalId' on $test and $existingTest")
      }
      seenExternalIds[externalId] = test.toString()
      testToExternalId[test.toString()] = externalId
    }
  }

  override fun testRunFailed(errorMessage: String) {
    _isTestRunFailed = true
  }

  override fun testFailed(test: TestIdentifier, trace: String) {}

  override fun testAssumptionFailure(test: TestIdentifier, trace: String) {}

  override fun testRunStopped(elapsedTime: Long) {}

  override fun testRunEnded(elapsedTime: Long, runMetrics: Map<String, String>) {}
}
