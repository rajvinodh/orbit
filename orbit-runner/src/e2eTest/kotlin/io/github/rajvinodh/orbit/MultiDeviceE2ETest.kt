package io.github.rajvinodh.orbit

import com.google.gson.JsonParser
import io.github.rajvinodh.orbit.config.RunConfig
import io.github.rajvinodh.orbit.domain.ExecutionMode
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/**
 * End-to-end test that runs Orbit in DISTRIBUTE mode across TWO real, connected devices/AVDs.
 * [E2ETest] and [DistributeE2ETest] only ever see a single device, so they can't catch bugs in the
 * shared-queue work-stealing path — this class is the first one that actually does.
 *
 * NOT run in CI yet (needs a second AVD wired into e2e.yml) — run locally first:
 * 1. Start two emulators/devices so `adb devices` lists 2+ lines ending in "device".
 * 2. cd test-app && ./gradlew :composeApp:assembleDebug :composeApp:assembleDebugAndroidTest
 * 3. cd .. && ./gradlew :orbit-runner:e2eTest --tests MultiDeviceE2ETest
 *
 * Scope: verifies DISTRIBUTE's correctness invariant (every test runs on exactly one device, no
 * duplication, no gaps) with real ADB and real device timing. It intentionally does NOT assert
 * *how* the 4 tests split across the 2 devices — with only 4 tests, a fast device can grab most or
 * all of them off the shared queue before a slower one finishes provisioning, so asserting a
 * particular split would be flaky.
 *
 * Mid-run disconnection with a real device (e.g. killing an emulator while it's executing) is
 * deliberately NOT covered here — reliably triggering that from a test is environment-specific and
 * risks flaking CI. That path stays covered by the FakeIDevice integration tests (DeviceWorkerTest
 * / MultiDeviceOrchestratorTest).
 */
class MultiDeviceE2ETest {

  companion object {
    private val outputDir =
      File(System.getProperty("orbit.e2e.outputDir", "build/e2e-output") + "-multidevice")
    private val reportDir = File(outputDir, "orbit-report")

    // Keep in sync with AppComposeTest — one entry per @Test method (excluding @Ignore)
    private const val EXPECTED_TOTAL = 4
    private const val EXPECTED_PASSED = 1
    private const val EXPECTED_FAILED = 1
    private const val EXPECTED_IGNORED = 2 // @Ignore + assumeTrue(false)

    private val testApkDir = File("../test-app/composeApp/build/outputs/apk/androidTest/debug")
    private val appApkDir = File("../test-app/composeApp/build/outputs/apk/debug")

    @JvmStatic
    @BeforeAll
    fun runOrbit() {
      val androidHome = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
      assumeTrue(androidHome != null, "Skipping: ANDROID_HOME/ANDROID_SDK_ROOT is not set")

      val onlineDeviceCount = countOnlineDevices(androidHome!!)
      assumeTrue(
        onlineDeviceCount >= 2,
        "Skipping: need 2+ connected devices, found $onlineDeviceCount " +
          "(start a second emulator/device and re-run)",
      )

      val testApk = testApkDir.listFiles()?.firstOrNull { it.name.endsWith(".apk") }
      assumeTrue(
        testApk != null,
        "Skipping: test-app androidTest APK not found under $testApkDir\n" +
          "  Run: cd test-app && ./gradlew :composeApp:assembleDebugAndroidTest",
      )

      val appApk = appApkDir.listFiles()?.firstOrNull { it.name.endsWith(".apk") }
      assumeTrue(
        appApk != null,
        "Skipping: test-app debug APK not found under $appApkDir\n" +
          "  Run: cd test-app && ./gradlew :composeApp:assembleDebug",
      )

      // No explicit serials — Orbit auto-discovers every connected device.
      val config =
        RunConfig.builder()
          .testApk(testApk!!)
          .otherApk(appApk!!)
          .appPackage("io.github.rajvinodh.orbit.testapp")
          .testPackage("io.github.rajvinodh.orbit.testapp.test")
          .testRunner("androidx.test.runner.AndroidJUnitRunner")
          .adbTimeout(java.time.Duration.ofSeconds(30))
          .availabilityTimeoutMin(1)
          .output(outputDir)
          .androidSdk(File(androidHome))
          .reportTitle("E2E — orbit test-app (multi-device DISTRIBUTE)")
          .executionMode(ExecutionMode.DISTRIBUTE)
          .build()

      // run() returns false when any test fails — expected here since one test intentionally fails
      OrbitRunner(config).run()
    }

    /** Counts `adb devices` lines ending in "device" (online, authorized). */
    private fun countOnlineDevices(androidHome: String): Int {
      val adb = File(androidHome, "platform-tools${File.separator}adb").absolutePath
      return try {
        val process = ProcessBuilder(adb, "devices").redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        output
          .lines()
          .drop(1) // header line: "List of devices attached"
          .count { it.trim().endsWith("device") }
      } catch (e: Exception) {
        0
      }
    }
  }

  @Test
  fun `data json exists and is valid json`() {
    val dataJson = File(reportDir, "data.json")
    assertThat(dataJson).exists()
    JsonParser.parseReader(dataJson.reader()) // throws if invalid JSON
  }

  @Test
  fun `at least two devices are present and completed`() {
    val devices =
      JsonParser.parseReader(File(reportDir, "data.json").reader())
        .asJsonObject["devices"]
        .asJsonArray
    assertThat(devices.size()).isGreaterThanOrEqualTo(2)
    devices.forEach { assertThat(it.asJsonObject["status"].asString).isEqualTo("COMPLETED") }
  }

  @Test
  fun `summary counts match expected regardless of device count`() {
    val summary =
      JsonParser.parseReader(File(reportDir, "data.json").reader())
        .asJsonObject["summary"]
        .asJsonObject
    assertThat(summary["total"].asInt).isEqualTo(EXPECTED_TOTAL)
    assertThat(summary["passed"].asInt).isEqualTo(EXPECTED_PASSED)
    assertThat(summary["failed"].asInt).isEqualTo(EXPECTED_FAILED)
    assertThat(summary["ignored"].asInt).isEqualTo(EXPECTED_IGNORED)
  }

  @Test
  fun `every unique test runs on exactly one device — no duplication, no gaps`() {
    val tests = allTests()
    assertThat(tests).hasSize(EXPECTED_TOTAL)

    val methodNames = tests.map { it.asJsonObject["methodName"].asString }
    assertThat(methodNames)
      .containsExactlyInAnyOrder(
        "mainActivity_displaysContentFromActivityLifecycle",
        "mainActivity_displaysContentFromActivityLifecycle_failure",
        "mainActivity_ignoredTest",
        "mainActivity_assumptionFailureTest",
      )
    // containsExactlyInAnyOrder above already proves no duplicates and no gaps — each of the 4
    // method names appears, and the collected list also has exactly 4 entries.
  }

  @Test
  fun `top-level tests aggregation is absent in DISTRIBUTE mode`() {
    val root = JsonParser.parseReader(File(reportDir, "data.json").reader()).asJsonObject
    assertThat(root["tests"].isJsonNull).isTrue()
  }

  @Test
  fun `junit xml is written per device`() {
    val xmlFiles =
      File(reportDir, "junit").listFiles { f ->
        f.name.startsWith("TEST-") && f.name.endsWith(".xml")
      }
    assertThat(xmlFiles).isNotEmpty()
    assertThat(xmlFiles!!.size).isGreaterThanOrEqualTo(2) // one per device
  }

  // ── helpers ────────────────────────────────────────────────────────────

  private fun allTests() =
    JsonParser.parseReader(File(reportDir, "data.json").reader())
      .asJsonObject["devices"]
      .asJsonArray
      .flatMap { it.asJsonObject["tests"].asJsonArray.toList() }
}
