package io.github.rajvinodh.orbit.listener

import com.android.ddmlib.testrunner.TestIdentifier
import io.github.rajvinodh.orbit.domain.TestStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class OrbitTestRunListenerTest {

  private lateinit var listener: OrbitTestRunListener
  private val test = TestIdentifier("com.example.FooTest", "testBar")

  @BeforeEach
  fun setup() {
    listener = OrbitTestRunListener()
  }

  @Test
  fun `testStarted creates entry and resets recentTestFailed`() {
    listener.testFailed(test, "boom") // set failed first
    listener.testStarted(test)
    assertThat(listener.didRecentTestFail()).isFalse()
  }

  @Test
  fun `testFailed sets FAIL status and recentTestFailed=true`() {
    listener.testStarted(test)
    listener.testFailed(test, "java.lang.AssertionError")
    assertThat(listener.didRecentTestFail()).isTrue()
    listener.testEnded(test, emptyMap())
    assertThat(listener.getLastResult()!!.status).isEqualTo(TestStatus.FAIL)
    assertThat(listener.getLastResult()!!.exception).isEqualTo("java.lang.AssertionError")
  }

  @Test
  fun `testAssumptionFailure sets ASSUMPTION_FAILURE and recentTestFailed=true`() {
    listener.testStarted(test)
    listener.testAssumptionFailure(test, "trace")
    assertThat(listener.didRecentTestFail()).isTrue()
    listener.testEnded(test, emptyMap())
    assertThat(listener.getLastResult()!!.status).isEqualTo(TestStatus.ASSUMPTION_FAILURE)
  }

  @Test
  fun `testIgnored sets IGNORED status`() {
    listener.testIgnored(test)
    listener.testEnded(test, emptyMap())
    assertThat(listener.getLastResult()!!.status).isEqualTo(TestStatus.IGNORED)
  }

  @Test
  fun `testEnded produces result with duration`() {
    listener.testStarted(test)
    listener.testEnded(test, emptyMap())
    val result = listener.getLastResult()!!
    assertThat(result.durationMs).isGreaterThanOrEqualTo(0)
    assertThat(result.testCase.methodName).isEqualTo("testBar")
  }

  @Test
  fun `testRunFailed sets runFailureMessage and recentTestFailed=true`() {
    listener.testRunFailed("instrumentation crashed")
    assertThat(listener.didRecentTestFail()).isTrue()
    assertThat(listener.runFailureMessage).isEqualTo("instrumentation crashed")
  }

  @Test
  fun `recentTestFailed resets to false on next testStarted`() {
    val t2 = TestIdentifier("com.example.FooTest", "testBaz")
    listener.testStarted(test)
    listener.testFailed(test, "err")
    assertThat(listener.didRecentTestFail()).isTrue()
    listener.testStarted(t2)
    assertThat(listener.didRecentTestFail()).isFalse()
  }

  @Test
  fun `passing test has PASS status`() {
    listener.testStarted(test)
    listener.testEnded(test, emptyMap())
    assertThat(listener.getLastResult()!!.status).isEqualTo(TestStatus.PASS)
    assertThat(listener.didRecentTestFail()).isFalse()
  }
}
