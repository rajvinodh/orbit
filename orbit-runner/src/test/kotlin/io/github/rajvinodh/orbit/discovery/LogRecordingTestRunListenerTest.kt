package io.github.rajvinodh.orbit.discovery

import com.android.ddmlib.testrunner.TestIdentifier
import io.github.rajvinodh.orbit.discovery.internal.LogRecordingTestRunListener
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LogRecordingTestRunListenerTest {

  private lateinit var listener: LogRecordingTestRunListener

  @BeforeEach
  fun setup() {
    listener = LogRecordingTestRunListener()
  }

  @Test
  fun `testStarted adds to activeTests`() {
    val test = TestIdentifier("com.example.Foo", "bar")
    listener.testRunStarted("run", 1)
    listener.testStarted(test)
    assertThat(listener.activeTests()).hasSize(1)
    assertThat(listener.activeTests()[0].className).isEqualTo("com.example.Foo")
  }

  @Test
  fun `testIgnored moves from active to ignored`() {
    val test = TestIdentifier("com.example.Foo", "bar")
    listener.testRunStarted("run", 1)
    listener.testStarted(test)
    listener.testIgnored(test)
    assertThat(listener.activeTests()).isEmpty()
    assertThat(listener.ignoredTests()).hasSize(1)
  }

  @Test
  fun `parameterised class suffix stripped`() {
    val paramTest = TestIdentifier("com.example.Foo[0]", "bar")
    listener.testRunStarted("run", 1)
    listener.testStarted(paramTest)
    assertThat(listener.activeTests()[0].className).isEqualTo("com.example.Foo")
  }

  @Test
  fun `isTestRunFailed false after normal run`() {
    listener.testRunStarted("run", 0)
    assertThat(listener.isTestRunFailed()).isFalse()
  }

  @Test
  fun `isTestRunFailed true after testRunFailed`() {
    listener.testRunStarted("run", 1)
    listener.testRunFailed("boom")
    assertThat(listener.isTestRunFailed()).isTrue()
  }

  @Test
  fun `testEnded stores annotations`() {
    val test = TestIdentifier("com.example.Foo", "bar")
    listener.testRunStarted("run", 1)
    listener.testStarted(test)
    listener.testEnded(test, mapOf("annotations" to "ClearLogCat, Flaky"))
    assertThat(listener.annotationsFor(test)).containsExactly("ClearLogCat", "Flaky")
  }

  @Test
  fun `testEnded stores clearAppDataAction`() {
    val test = TestIdentifier("com.example.Foo", "bar")
    listener.testRunStarted("run", 1)
    listener.testStarted(test)
    listener.testEnded(test, mapOf("clearAppDataAction" to "BEFORE"))
    assertThat(listener.clearAppDataActionFor(test)).isEqualTo("BEFORE")
  }

  @Test
  fun `testEnded stores externalId`() {
    val test = TestIdentifier("com.example.Foo", "bar")
    listener.testRunStarted("run", 1)
    listener.testStarted(test)
    listener.testEnded(test, mapOf("externalId" to "C1234"))
    assertThat(listener.externalIdFor(test)).isEqualTo("C1234")
  }

  @Test
  fun `duplicate externalId throws`() {
    val t1 = TestIdentifier("com.example.Foo", "a")
    val t2 = TestIdentifier("com.example.Foo", "b")
    listener.testRunStarted("run", 2)
    listener.testStarted(t1)
    listener.testStarted(t2)
    listener.testEnded(t1, mapOf("externalId" to "C1"))
    assertThatThrownBy { listener.testEnded(t2, mapOf("externalId" to "C1")) }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("C1")
  }
}
