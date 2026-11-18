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
 * End-to-end test that runs Orbit against the bundled test-app on a live AVD.
 *
 * Preconditions (all checked via assumeTrue — missing ones skip, not fail):
 * 1. ANDROID_HOME is set
 * 2. test-app APKs are built: cd test-app && ./gradlew :composeApp:assembleDebug
 *    :composeApp:assembleDebugAndroidTest
 * 3. A device/emulator is online (Orbit will abort if none is found)
 *
 * Run manually: cd test-app && ./gradlew :composeApp:assembleDebug
 * :composeApp:assembleDebugAndroidTest cd .. && ./gradlew :orbit-runner:e2eTest
 */
class E2ETest {

  companion object {
    private val outputDir = File(System.getProperty("orbit.e2e.outputDir", "build/e2e-output"))
    private val reportDir = File(outputDir, "orbit-report")

    // Keep in sync with AppComposeTest — one entry per @Test method (excluding @Ignore)
    private const val EXPECTED_TOTAL = 4
    private const val EXPECTED_PASSED = 1
    private const val EXPECTED_FAILED = 1
    private const val EXPECTED_IGNORED = 2 // @Ignore + assumeTrue(false)

    // APKs produced by :composeApp:assembleDebug / assembleDebugAndroidTest
    private val testApkDir = File("../test-app/composeApp/build/outputs/apk/androidTest/debug")
    private val appApkDir = File("../test-app/composeApp/build/outputs/apk/debug")

    @JvmStatic
    @BeforeAll
    fun runOrbit() {
      // ── preconditions (skip, not fail, when not met) ───────────────
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

      // ── run ────────────────────────────────────────────────────────
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
          .reportTitle("E2E — orbit test-app")
          .recordVideo(true)
          .appiumSettingsVersion("7.1.4")
          .trackLogcat(true)
          .codeCoverage(true)
          .executionMode(ExecutionMode.FANOUT)
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
  fun `summary counts match expected`() {
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
  fun `ignored test is captured with IGNORED status`() {
    // FANOUT replicates every test on every connected device, so the raw count scales with
    // device count — assert on the unique method name instead of a fixed size.
    val ignored = allTests().filter { it.asJsonObject["status"].asString == "IGNORED" }
    assertThat(ignored).isNotEmpty()
    assertThat(ignored.map { it.asJsonObject["methodName"].asString }.distinct())
      .containsExactly("mainActivity_ignoredTest")
  }

  @Test
  fun `assumption failure test is captured with ASSUMPTION_FAILURE status`() {
    // Same reasoning as above — don't assume a single device.
    val assumed = allTests().filter { it.asJsonObject["status"].asString == "ASSUMPTION_FAILURE" }
    assertThat(assumed).isNotEmpty()
    assertThat(assumed.map { it.asJsonObject["methodName"].asString }.distinct())
      .containsExactly("mainActivity_assumptionFailureTest")
  }

  @Test
  fun `index html exists`() {
    assertThat(File(reportDir, "index.html")).exists()
  }

  @Test
  fun `junit xml exists and contains expected elements`() {
    val xmlFiles =
      File(reportDir, "junit").listFiles { f ->
        f.name.startsWith("TEST-") && f.name.endsWith(".xml")
      }
    assertThat(xmlFiles).isNotEmpty()
    val xml = xmlFiles!![0].readText()
    assertThat(xml).contains("<testsuite ")
    assertThat(xml).contains("<testcase ")
    assertThat(xml).contains("<failure ")
    assertThat(xml).contains("<skipped")
  }

  @Test
  fun `fanout aggregation lists one entry per unique test with worst-wins status`() {
    val tests = fanoutTests()
    assertThat(tests).hasSize(EXPECTED_TOTAL)

    val byMethod = tests.associateBy { it.asJsonObject["methodName"].asString }
    assertThat(byMethod.keys)
      .containsExactlyInAnyOrder(
        "mainActivity_displaysContentFromActivityLifecycle",
        "mainActivity_displaysContentFromActivityLifecycle_failure",
        "mainActivity_ignoredTest",
        "mainActivity_assumptionFailureTest",
      )

    val passing = byMethod["mainActivity_displaysContentFromActivityLifecycle"]!!.asJsonObject
    assertThat(passing["aggregatedStatus"].asString).isEqualTo("PASS")
    assertThat(passing["deviceStatuses"].asJsonObject.entrySet()).isNotEmpty()
    assertThat(passing["deviceStatuses"].asJsonObject.entrySet().map { it.value.asString })
      .allMatch { it == "PASS" }

    val failing =
      byMethod["mainActivity_displaysContentFromActivityLifecycle_failure"]!!.asJsonObject
    assertThat(failing["aggregatedStatus"].asString).isEqualTo("FAIL")
    assertThat(failing["deviceStatuses"].asJsonObject.entrySet().map { it.value.asString })
      .allMatch { it == "FAIL" }
  }

  @Test
  fun `screenshots are captured and the files exist on disk`() {
    for (methodName in
      listOf(
        "mainActivity_displaysContentFromActivityLifecycle",
        "mainActivity_displaysContentFromActivityLifecycle_failure",
      )) {
      val test = deviceTest(methodName)
      val screenshots = test["screenshots"].asJsonArray
      assertThat(screenshots).isNotEmpty()
      screenshots.forEach { assertThat(File(reportDir, it.asString)).exists() }
    }
  }

  @Test
  fun `video is kept only for the failing test`() {
    val passing = deviceTest("mainActivity_displaysContentFromActivityLifecycle")
    assertThat(passing["video"].isJsonNull).isTrue()

    val failing = deviceTest("mainActivity_displaysContentFromActivityLifecycle_failure")
    assertThat(failing["video"].isJsonNull).isFalse()
    assertThat(File(reportDir, failing["video"].asString)).exists()
  }

  @Test
  fun `logcat is captured only for the failing test`() {
    val passing = deviceTest("mainActivity_displaysContentFromActivityLifecycle")
    assertThat(passing["logcat"].asJsonArray).isEmpty()

    val failing = deviceTest("mainActivity_displaysContentFromActivityLifecycle_failure")
    assertThat(failing["logcat"].asJsonArray).isNotEmpty()
  }

  @Test
  fun `code coverage is merged per-device and across devices`() {
    val coverageDir = File(outputDir, "coverage")
    val serials =
      JsonParser.parseReader(File(reportDir, "data.json").reader())
        .asJsonObject["devices"]
        .asJsonArray
        .map { it.asJsonObject["serial"].asString }

    serials.forEach { serial -> assertThat(File(coverageDir, "$serial/coverage.ec")).exists() }
    assertThat(File(coverageDir, "merged-coverage.ec")).exists()
  }

  // ── helpers ────────────────────────────────────────────────────────────

  private fun allTests() =
    JsonParser.parseReader(File(reportDir, "data.json").reader())
      .asJsonObject["devices"]
      .asJsonArray
      .flatMap { it.asJsonObject["tests"].asJsonArray.toList() }

  private fun fanoutTests() =
    JsonParser.parseReader(File(reportDir, "data.json").reader())
      .asJsonObject["tests"]
      .asJsonArray
      .toList()

  /** First device's entry for the given test method — fine since FANOUT runs it on every device. */
  private fun deviceTest(methodName: String) =
    allTests().first { it.asJsonObject["methodName"].asString == methodName }.asJsonObject
}
