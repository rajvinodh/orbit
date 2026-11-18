package io.github.rajvinodh.orbit

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.IDevice
import com.android.ddmlib.testrunner.ITestRunListener
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner
import com.android.ddmlib.testrunner.TestIdentifier
import io.github.rajvinodh.orbit.config.RunConfig
import io.github.rajvinodh.orbit.device.DeviceProvisioner
import io.github.rajvinodh.orbit.device.ProvisionResult
import io.github.rajvinodh.orbit.discovery.TestDiscovery
import io.github.rajvinodh.orbit.domain.*
import io.github.rajvinodh.orbit.orchestration.MultiDeviceOrchestrator
import io.mockk.*
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * End-to-end integration tests for the full 5-phase pipeline.
 *
 * Real orchestration and session logic runs; only the DDMLib boundary (TestDiscovery and
 * RemoteAndroidTestRunner) is intercepted so no physical device is required.
 */
class PipelineIntegrationTest {

  @TempDir lateinit var outputDir: File

  private val adb = mockk<AndroidDebugBridge>(relaxed = true)
  private val device1 = mockk<IDevice>(relaxed = true)
  private val device2 = mockk<IDevice>(relaxed = true)
  private val provisioner = mockk<DeviceProvisioner>()

  private val serial1 = "emulator-5554"
  private val serial2 = "emulator-5556"

  private val testA = TestCase("com.example.LoginTest", "testLogin")
  private val testB = TestCase("com.example.LoginTest", "testLogout")
  private val testC = TestCase("com.example.ProfileTest", "testEdit")

  @BeforeEach
  fun setUp() {
    every { device1.serialNumber } returns serial1
    every { device2.serialNumber } returns serial2
    every { device1.state } returns IDevice.DeviceState.ONLINE
    every { device2.state } returns IDevice.DeviceState.ONLINE
    every { adb.devices } returns arrayOf(device1, device2)
    every { provisioner.provision(any(), any()) } returns ProvisionResult.SUCCESS

    mockkConstructor(TestDiscovery::class)
    mockkConstructor(RemoteAndroidTestRunner::class)
  }

  @AfterEach
  fun tearDown() {
    unmockkConstructor(TestDiscovery::class)
    unmockkConstructor(RemoteAndroidTestRunner::class)
  }

  // ─────────────────────────────────────────────────────────────────────

  @Test
  fun `single device — two passing tests appear in summary`() {
    stubDiscovery(testA, testB)
    runnerAlwaysPasses()

    val summary = run(serial1)

    assertThat(summary.results).hasSize(1)
    assertThat(totalResults(summary)).isEqualTo(2)
    assertThat(summary.results[serial1]!!.testResults.values.map { it.status })
      .containsOnly(TestStatus.PASS)
  }

  @Test
  fun `failing test is captured with FAIL status and exception`() {
    stubDiscovery(testA)
    runnerAlwaysFails("java.lang.AssertionError: expected true was false")

    val summary = run(serial1)

    val result = summary.results[serial1]!!.testResults[testA]!!
    assertThat(result.status).isEqualTo(TestStatus.FAIL)
    assertThat(result.exception).contains("AssertionError")
  }

  @Test
  fun `two devices — all three tests executed across both`() {
    stubDiscovery(testA, testB, testC)
    runnerAlwaysPasses()

    val summary = run(serial1, serial2)

    assertThat(summary.results).hasSize(2)
    assertThat(totalResults(summary)).isEqualTo(3)
  }

  @Test
  fun `tests remaining in queue after all provisioning fails land in missed`() {
    stubDiscovery(testA, testB)
    // First call is Phase 1 discovery provisioning (must succeed so discovery can run);
    // every subsequent call is Phase 2 device provisioning, which fails for all devices.
    every { provisioner.provision(any(), any()) } returnsMany
      listOf(ProvisionResult.SUCCESS, ProvisionResult.PROVISIONING_FAILED)

    val summary = run(serial1)

    assertThat(summary.missedTests).containsExactlyInAnyOrder(testA, testB)
    assertThat(totalResults(summary)).isEqualTo(0)
  }

  @Test
  fun `report files are written and data json contains expected fields`() {
    stubDiscovery(testA, testB)
    runnerAlwaysPasses()

    run(config(serial1).toBuilder().reportTitle("Integration Suite").build())

    val reportDir = File(outputDir, "orbit-report")
    assertThat(File(reportDir, "index.html")).exists()
    assertThat(File(reportDir, "data.json")).exists()

    val json = File(reportDir, "data.json").readText()
    assertThat(json).contains("Integration Suite")
    assertThat(json).contains("\"passed\"")
    assertThat(json).contains("\"devices\"")
  }

  @Test
  fun `mixed results — first test fails second passes`() {
    stubDiscovery(testA, testB)

    val callCount = AtomicInteger(0)
    every {
      anyConstructed<RemoteAndroidTestRunner>().run(any<Collection<ITestRunListener>>())
    } answers
      {
        val n = callCount.incrementAndGet()
        val listeners = firstArg<Collection<ITestRunListener>>()
        val id = TestIdentifier("com.example.Test", "t")
        listeners.forEach { it.testRunStarted("run", 1) }
        listeners.forEach { it.testStarted(id) }
        if (n == 1) listeners.forEach { it.testFailed(id, "assertion error on attempt 1") }
        listeners.forEach { it.testEnded(id, emptyMap()) }
        listeners.forEach { it.testRunEnded(100, emptyMap()) }
      }

    val summary = run(serial1)

    val statuses = summary.results[serial1]!!.testResults.values.map { it.status }
    assertThat(statuses).containsExactlyInAnyOrder(TestStatus.FAIL, TestStatus.PASS)
  }

  @Test
  fun `device disconnect mid-run — session marked disconnected in result`() {
    stubDiscovery(testA, testB)

    every {
      anyConstructed<RemoteAndroidTestRunner>().run(any<Collection<ITestRunListener>>())
    } throws RuntimeException("device offline: emulator-5554")

    val summary = run(serial1)

    assertThat(summary.results[serial1]!!.status).isEqualTo(DeviceStatus.DISCONNECTED)
  }

  // ── helpers ────────────────────────────────────────────────────────────

  private fun run(vararg serials: String) = run(config(*serials))

  private fun run(cfg: RunConfig) = MultiDeviceOrchestrator(cfg, provisioner).run(adb)

  private fun config(vararg serials: String) =
    RunConfig.builder()
      .testApk(File(outputDir, "test.apk"))
      .appPackage("com.example")
      .apply { serials.forEach { serial(it) } }
      .output(outputDir)
      .build()

  private fun stubDiscovery(vararg tests: TestCase) {
    val builder = DiscoveryResult.builder().activeTests(tests.toList())
    tests.forEach { builder.metadataEntry(it, TestMetadata.builder().testCase(it).build()) }
    every { anyConstructed<TestDiscovery>().run(any()) } returns builder.build()
  }

  private fun runnerAlwaysPasses() {
    every {
      anyConstructed<RemoteAndroidTestRunner>().run(any<Collection<ITestRunListener>>())
    } answers
      {
        val listeners = firstArg<Collection<ITestRunListener>>()
        val id = TestIdentifier("com.example.Test", "t")
        listeners.forEach { it.testRunStarted("run", 1) }
        listeners.forEach { it.testStarted(id) }
        listeners.forEach { it.testEnded(id, emptyMap()) }
        listeners.forEach { it.testRunEnded(100, emptyMap()) }
      }
  }

  private fun runnerAlwaysFails(trace: String) {
    every {
      anyConstructed<RemoteAndroidTestRunner>().run(any<Collection<ITestRunListener>>())
    } answers
      {
        val listeners = firstArg<Collection<ITestRunListener>>()
        val id = TestIdentifier("com.example.Test", "t")
        listeners.forEach { it.testRunStarted("run", 1) }
        listeners.forEach { it.testStarted(id) }
        listeners.forEach { it.testFailed(id, trace) }
        listeners.forEach { it.testEnded(id, emptyMap()) }
        listeners.forEach { it.testRunEnded(100, emptyMap()) }
      }
  }

  private fun totalResults(summary: OrbitSummary) =
    summary.results.values.sumOf { it.testResults.size }
}
