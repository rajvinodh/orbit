package io.github.rajvinodh.orbit.orchestration

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.IDevice
import io.github.rajvinodh.orbit.config.RunConfig
import io.github.rajvinodh.orbit.device.DeviceProvisioner
import io.github.rajvinodh.orbit.device.ProvisionResult
import io.github.rajvinodh.orbit.discovery.TestDiscovery
import io.github.rajvinodh.orbit.domain.DiscoveryResult
import io.github.rajvinodh.orbit.domain.ExecutionMode
import io.github.rajvinodh.orbit.domain.TestCase
import io.github.rajvinodh.orbit.domain.TestMetadata
import io.mockk.*
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class MultiDeviceOrchestratorTest {

  @TempDir lateinit var outputDir: File

  private val adb = mockk<AndroidDebugBridge>(relaxed = true)
  private val device1 = mockk<IDevice>(relaxed = true)
  private val device2 = mockk<IDevice>(relaxed = true)
  private val provisioner = mockk<DeviceProvisioner>()

  private val serial1 = "emulator-5554"
  private val serial2 = "emulator-5556"

  private lateinit var config: RunConfig
  private lateinit var emptyDiscovery: DiscoveryResult

  @BeforeEach
  fun setUp() {
    every { device1.serialNumber } returns serial1
    every { device2.serialNumber } returns serial2
    every { device1.state } returns IDevice.DeviceState.ONLINE
    every { device2.state } returns IDevice.DeviceState.ONLINE
    every { adb.devices } returns arrayOf(device1, device2)

    config =
      RunConfig.builder()
        .testApk(File(outputDir, "test.apk"))
        .appPackage("com.example")
        .serial(serial1)
        .serial(serial2)
        .output(outputDir)
        .build()

    emptyDiscovery = DiscoveryResult.builder().activeTests(emptyList()).build()

    mockkConstructor(TestDiscovery::class)
  }

  @AfterEach
  fun tearDown() {
    unmockkConstructor(TestDiscovery::class)
  }

  // ── Phase 1: Discovery ─────────────────────────────────────────────────

  @Test
  fun `throws when all devices are offline`() {
    every { device1.state } returns IDevice.DeviceState.OFFLINE
    every { device2.state } returns IDevice.DeviceState.OFFLINE

    assertThatThrownBy { MultiDeviceOrchestrator(config, provisioner).run(adb) }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("Discovery failed")
  }

  @Test
  fun `throws when discovery throws on all online devices`() {
    every { provisioner.provision(any(), any()) } returns ProvisionResult.SUCCESS
    every { anyConstructed<TestDiscovery>().run(any()) } throws RuntimeException("adb error")

    assertThatThrownBy { MultiDeviceOrchestrator(config, provisioner).run(adb) }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("Discovery failed")
  }

  @Test
  fun `skips offline device and uses second for discovery`() {
    every { device1.state } returns IDevice.DeviceState.OFFLINE
    // device2 is ONLINE — provision succeeds for discovery but TestDiscovery throws
    every { provisioner.provision(device2, any()) } returns ProvisionResult.SUCCESS
    every { anyConstructed<TestDiscovery>().run(device2) } throws RuntimeException("fail")

    assertThatThrownBy { MultiDeviceOrchestrator(config, provisioner).run(adb) }
      .isInstanceOf(IllegalStateException::class.java)

    // Discovery was attempted on device2, not device1
    verify { anyConstructed<TestDiscovery>().run(device2) }
    verify(exactly = 0) { anyConstructed<TestDiscovery>().run(device1) }
  }

  // ── Phase 2: Provisioning ──────────────────────────────────────────────

  @Test
  fun `provisioning failures result in empty device results`() {
    every { anyConstructed<TestDiscovery>().run(any()) } returns emptyDiscovery
    // device1: SUCCESS for discovery, then FAIL in phase 2; device2: always FAIL
    stubProvisionerForDiscoveryThenFail()

    val summary = MultiDeviceOrchestrator(config, provisioner).run(adb)

    assertThat(summary.results).isEmpty()
  }

  @Test
  fun `skipped serials are never provisioned`() {
    every { anyConstructed<TestDiscovery>().run(any()) } returns emptyDiscovery
    // device1: SUCCESS for discovery, then FAIL in phase 2
    every { provisioner.provision(device1, any()) } returns
      ProvisionResult.SUCCESS andThen
      ProvisionResult.PROVISIONING_FAILED
    // device2 should never be provisioned (skipSerial)

    val configWithSkip = config.toBuilder().skipSerial(serial2).build()
    MultiDeviceOrchestrator(configWithSkip, provisioner).run(adb)

    verify(exactly = 0) { provisioner.provision(device2, any()) }
  }

  // ── Summary and Report ─────────────────────────────────────────────────

  @Test
  fun `summary uses explicit reportTitle when provided`() {
    every { anyConstructed<TestDiscovery>().run(any()) } returns emptyDiscovery
    stubProvisionerForDiscoveryThenFail()

    val summary =
      MultiDeviceOrchestrator(config.toBuilder().reportTitle("My Suite").build(), provisioner)
        .run(adb)

    assertThat(summary.title).isEqualTo("My Suite")
  }

  @Test
  fun `summary auto-title uses appPackage when reportTitle is null`() {
    every { anyConstructed<TestDiscovery>().run(any()) } returns emptyDiscovery
    stubProvisionerForDiscoveryThenFail()

    val summary = MultiDeviceOrchestrator(config, provisioner).run(adb)

    assertThat(summary.title).startsWith("com.example ·")
  }

  @Test
  fun `orbit-report index and data files are created`() {
    every { anyConstructed<TestDiscovery>().run(any()) } returns emptyDiscovery
    stubProvisionerForDiscoveryThenFail()

    MultiDeviceOrchestrator(config, provisioner).run(adb)

    assertThat(File(outputDir, "orbit-report/index.html")).exists()
    assertThat(File(outputDir, "orbit-report/data.json")).exists()
  }

  @Test
  fun `missed tests from selection strategy appear in summary`() {
    val tc = TestCase("com.Foo", "testBar")
    val meta = TestMetadata.builder().testCase(tc).build()
    val discovery =
      DiscoveryResult.builder().activeTests(listOf(tc)).metadataEntry(tc, meta).build()

    every { anyConstructed<TestDiscovery>().run(any()) } returns discovery
    // device1: SUCCESS for discovery, then FAIL in phase 2 — no workers run, tc drains to missed
    stubProvisionerForDiscoveryThenFail()

    val summary = MultiDeviceOrchestrator(config, provisioner).run(adb)

    assertThat(summary.missedTests).contains(tc)
  }

  // ── ExecutionMode ──────────────────────────────────────────────────────

  @Test
  fun `fanout executionMode is reflected in summary`() {
    every { anyConstructed<TestDiscovery>().run(any()) } returns emptyDiscovery
    stubProvisionerForDiscoveryThenFail()

    val fanoutConfig = config.toBuilder().executionMode(ExecutionMode.FANOUT).build()
    val summary = MultiDeviceOrchestrator(fanoutConfig, provisioner).run(adb)

    assertThat(summary.executionMode).isEqualTo(ExecutionMode.FANOUT)
  }

  @Test
  fun `distribute executionMode is default`() {
    every { anyConstructed<TestDiscovery>().run(any()) } returns emptyDiscovery
    stubProvisionerForDiscoveryThenFail()

    val summary = MultiDeviceOrchestrator(config, provisioner).run(adb)

    assertThat(summary.executionMode).isEqualTo(ExecutionMode.DISTRIBUTE)
  }

  // ── helpers ────────────────────────────────────────────────────────────

  /**
   * device1 returns SUCCESS for the discovery provision call, then PROVISIONING_FAILED for phase 2.
   * device2 always returns PROVISIONING_FAILED (discovery never reaches it because device1
   * succeeds).
   */
  private fun stubProvisionerForDiscoveryThenFail() {
    every { provisioner.provision(device1, any()) } returns
      ProvisionResult.SUCCESS andThen
      ProvisionResult.PROVISIONING_FAILED
    every { provisioner.provision(device2, any()) } returns ProvisionResult.PROVISIONING_FAILED
  }
}
