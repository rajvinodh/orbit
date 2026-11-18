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
 * End-to-end test that runs Orbit in DISTRIBUTE mode (the default) against the bundled test-app on
 * a live AVD. [E2ETest] already covers FANOUT and its artifact collection (screenshots, video,
 * logcat, coverage); this class only asserts the report-shape differences that are specific to
 * DISTRIBUTE, so it doesn't repeat those checks.
 *
 * Preconditions — same as [E2ETest]: ANDROID_HOME set, test-app APKs built, a device online.
 */
class DistributeE2ETest {

  companion object {
    private val outputDir =
      File(System.getProperty("orbit.e2e.outputDir", "build/e2e-output") + "-distribute")
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
      val androidHome = System.getenv("ANDROID_HOME")
      assumeTrue(androidHome != null, "Skipping E2E: ANDROID_HOME is not set")

      val testApk = testApkDir.listFiles()?.firstOrNull { it.name.endsWith(".apk") }
      assumeTrue(
        testApk != null,
        "Skipping E2E: test-app androidTest APK not found under $testApkDir\n" +
          "  Run: cd test-app && ./gradlew :composeApp:assembleDebugAndroidTest",
      )

      val appApk = appApkDir.listFiles()?.firstOrNull { it.name.endsWith(".apk") }
      assumeTrue(
        appApk != null,
        "Skipping E2E: test-app debug APK not found under $appApkDir\n" +
          "  Run: cd test-app && ./gradlew :composeApp:assembleDebug",
      )

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
          .androidSdk(File(androidHome!!))
          .reportTitle("E2E — orbit test-app (DISTRIBUTE)")
          .executionMode(ExecutionMode.DISTRIBUTE)
          .build()

      // run() returns false when any test fails — expected here since one test intentionally fails
      OrbitRunner(config).run()
    }
  }

  @Test
  fun `data json exists and is valid json`() {
    val dataJson = File(reportDir, "data.json")
    assertThat(dataJson).exists()
    JsonParser.parseReader(dataJson.reader()) // throws if invalid JSON
  }

  @Test
  fun `meta reports DISTRIBUTE execution mode`() {
    val meta =
      JsonParser.parseReader(File(reportDir, "data.json").reader())
        .asJsonObject["meta"]
        .asJsonObject
    assertThat(meta["executionMode"].asString).isEqualTo("DISTRIBUTE")
  }

  @Test
  fun `top-level tests aggregation is absent in DISTRIBUTE mode`() {
    val root = JsonParser.parseReader(File(reportDir, "data.json").reader()).asJsonObject
    assertThat(root["tests"].isJsonNull).isTrue()
  }

  @Test
  fun `summary counts match expected with each test run exactly once`() {
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
  fun `every unique test appears exactly once across all devices`() {
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
  }

  @Test
  fun `junit xml exists and contains expected elements`() {
    val xmlFiles =
      File(reportDir, "junit").listFiles { f ->
        f.name.startsWith("TEST-") && f.name.endsWith(".xml")
      }
    assertThat(xmlFiles).isNotEmpty()
    // DISTRIBUTE splits the 4 tests across however many devices are connected, so each
    // per-device file only has a disjoint subset — combine them before checking for elements
    // that may have landed on a different device than an arbitrary single file.
    val xml = xmlFiles!!.joinToString("\n") { it.readText() }
    assertThat(xml).contains("<testsuite ")
    assertThat(xml).contains("<testcase ")
    assertThat(xml).contains("<failure ")
    assertThat(xml).contains("<skipped")
  }

  // ── helpers ────────────────────────────────────────────────────────────

  private fun allTests() =
    JsonParser.parseReader(File(reportDir, "data.json").reader())
      .asJsonObject["devices"]
      .asJsonArray
      .flatMap { it.asJsonObject["tests"].asJsonArray.toList() }
}
