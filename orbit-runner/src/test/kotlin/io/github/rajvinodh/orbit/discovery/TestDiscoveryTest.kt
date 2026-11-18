package io.github.rajvinodh.orbit.discovery

import com.android.ddmlib.testrunner.TestIdentifier
import io.github.rajvinodh.orbit.config.RunConfig
import io.github.rajvinodh.orbit.discovery.internal.LogRecordingTestRunListener
import io.github.rajvinodh.orbit.domain.DiscoveryResult
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class TestDiscoveryTest {

  private val config = RunConfig.builder().testApk(File("app-test.apk")).build()

  private fun discovery() = TestDiscovery(config)

  @Test
  fun `activeTests and ignoredTests extracted correctly`() {
    val result =
      drive(
        active = listOf(id("com.Foo", "a"), id("com.Foo", "b")),
        ignored = listOf(id("com.Foo", "c")),
      )
    assertThat(result.activeTests).hasSize(2)
    assertThat(result.ignoredTests).hasSize(1)
    assertThat(result.ignoredTests[0].methodName).isEqualTo("c")
  }

  @Test
  fun `ClearAppData BEFORE sets clearBefore=true clearAfter=false`() {
    val t = id("com.Foo", "a")
    val meta =
      drive(active = listOf(t), extras = mapOf(t to mapOf("clearAppDataAction" to "BEFORE")))
        .metadata
        .values
        .first()
    assertThat(meta.isClearAppDataBefore).isTrue()
    assertThat(meta.isClearAppDataAfter).isFalse()
  }

  @Test
  fun `ClearAppData AFTER sets clearBefore=false clearAfter=true`() {
    val t = id("com.Foo", "a")
    val meta =
      drive(active = listOf(t), extras = mapOf(t to mapOf("clearAppDataAction" to "AFTER")))
        .metadata
        .values
        .first()
    assertThat(meta.isClearAppDataBefore).isFalse()
    assertThat(meta.isClearAppDataAfter).isTrue()
  }

  @Test
  fun `ClearAppData BOTH_BEFORE_AFTER sets both=true`() {
    val t = id("com.Foo", "a")
    val meta =
      drive(
          active = listOf(t),
          extras = mapOf(t to mapOf("clearAppDataAction" to "BOTH_BEFORE_AFTER")),
        )
        .metadata
        .values
        .first()
    assertThat(meta.isClearAppDataBefore).isTrue()
    assertThat(meta.isClearAppDataAfter).isTrue()
  }

  @Test
  fun `ClearLogCat annotation sets clearLogcatBefore=true`() {
    val t = id("com.Foo", "a")
    val meta =
      drive(active = listOf(t), extras = mapOf(t to mapOf("annotations" to "ClearLogCat")))
        .metadata
        .values
        .first()
    assertThat(meta.isClearLogcatBefore).isTrue()
  }

  @Test
  fun `externalId annotation populates byExternalId`() {
    val t = id("com.Foo", "a")
    val result = drive(active = listOf(t), extras = mapOf(t to mapOf("externalId" to "C999")))
    assertThat(result.byExternalId).containsKey("C999")
    assertThat(result.metadata.values.first().externalId).isEqualTo("C999")
  }

  @Test
  fun `parameterised class suffix stripped`() {
    val result = drive(active = listOf(id("com.Foo[0]", "a")))
    assertThat(result.activeTests[0].className).isEqualTo("com.Foo")
  }

  @Test
  fun `isTestRunFailed=true throws RuntimeException`() {
    assertThatThrownBy { drive(active = emptyList(), runFailed = true) }
      .isInstanceOf(RuntimeException::class.java)
  }

  @Test
  fun `duplicate externalId throws`() {
    val t1 = id("com.Foo", "a")
    val t2 = id("com.Foo", "b")
    assertThatThrownBy {
        drive(
          active = listOf(t1, t2),
          extras = mapOf(t1 to mapOf("externalId" to "C1"), t2 to mapOf("externalId" to "C1")),
        )
      }
      .isInstanceOf(IllegalStateException::class.java)
  }

  // --- helpers ---

  private fun id(cls: String, method: String) = TestIdentifier(cls, method)

  private fun drive(
    active: List<TestIdentifier>,
    ignored: List<TestIdentifier> = emptyList(),
    extras: Map<TestIdentifier, Map<String, String>> = emptyMap(),
    runFailed: Boolean = false,
  ): DiscoveryResult {
    val listener = LogRecordingTestRunListener()
    listener.testRunStarted("run", active.size + ignored.size)
    active.forEach { listener.testStarted(it) }
    ignored.forEach {
      listener.testStarted(it)
      listener.testIgnored(it)
    }
    (active + ignored).forEach { listener.testEnded(it, extras[it] ?: emptyMap()) }
    if (runFailed) listener.testRunFailed("forced")
    return discovery().buildDiscoveryResult(listener)
  }
}
