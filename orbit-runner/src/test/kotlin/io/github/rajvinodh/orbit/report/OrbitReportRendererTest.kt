package io.github.rajvinodh.orbit.report

import com.google.gson.JsonParser
import io.github.rajvinodh.orbit.domain.*
import io.github.rajvinodh.orbit.domain.ExecutionMode
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class OrbitReportRendererTest {

  @TempDir lateinit var outputDir: File

  private val renderer = OrbitReportRenderer()

  // ── data.json meta ─────────────────────────────────────────────────────

  @Test
  fun `meta block contains title, timestamps, and orbitVersion`() {
    val summary = buildSummary(title = "My Suite · 2026-04-25")

    renderer.render(summary, outputDir)

    val meta = dataJson()["meta"].asJsonObject
    assertThat(meta["title"].asString).isEqualTo("My Suite · 2026-04-25")
    assertThat(meta["orbitVersion"].asString).isEqualTo("1.0.0")
    assertThat(meta["startedAt"].asLong).isEqualTo(summary.startedAt)
    assertThat(meta["durationMs"].asLong).isEqualTo(summary.durationMs)
  }

  @Test
  fun `null title is auto-generated from startedAt`() {
    val summary = buildSummary(title = null)

    renderer.render(summary, outputDir)

    val title = dataJson()["meta"].asJsonObject["title"].asString
    assertThat(title).startsWith("Orbit Report ·")
  }

  // ── data.json summary counts ────────────────────────────────────────────

  @Test
  fun `summary counts are correct across devices`() {
    val t1 = makeTestResult("com.Foo", "pass1", TestStatus.PASS)
    val t2 = makeTestResult("com.Foo", "fail1", TestStatus.FAIL)
    val t3 = makeTestResult("com.Foo", "skip1", TestStatus.IGNORED)
    val dr = makeDeviceResult("emu-1", listOf(t1, t2, t3))
    val summary = buildSummary(devices = listOf(dr))

    renderer.render(summary, outputDir)

    val s = dataJson()["summary"].asJsonObject
    assertThat(s["total"].asInt).isEqualTo(3)
    assertThat(s["passed"].asInt).isEqualTo(1)
    assertThat(s["failed"].asInt).isEqualTo(1)
    assertThat(s["ignored"].asInt).isEqualTo(1)
  }

  @Test
  fun `missed tests are counted in total and ignored`() {
    val missed = TestCase("com.Foo", "missedTest")
    val summary = buildSummary(devices = emptyList(), missed = listOf(missed))

    renderer.render(summary, outputDir)

    val s = dataJson()["summary"].asJsonObject
    assertThat(s["total"].asInt).isEqualTo(1)
    assertThat(s["ignored"].asInt).isEqualTo(1)
  }

  // ── data.json devices ───────────────────────────────────────────────────

  @Test
  fun `device block contains serial, status, and model info`() {
    val details = DeviceDetails("Pixel 7", "13", 33)
    val dr = makeDeviceResult("emulator-5554", emptyList(), details = details)
    val summary = buildSummary(devices = listOf(dr))

    renderer.render(summary, outputDir)

    val device = dataJson()["devices"].asJsonArray[0].asJsonObject
    assertThat(device["serial"].asString).isEqualTo("emulator-5554")
    assertThat(device["status"].asString).isEqualTo("COMPLETED")
    assertThat(device["model"].asString).isEqualTo("Pixel 7")
    assertThat(device["apiLevel"].asInt).isEqualTo(33)
    assertThat(device["osVersion"].asString).isEqualTo("13")
  }

  @Test
  fun `device without details has null model fields`() {
    val dr = makeDeviceResult("emu-1", emptyList(), details = null)
    renderer.render(buildSummary(devices = listOf(dr)), outputDir)

    val device = dataJson()["devices"].asJsonArray[0].asJsonObject
    assertThat(device["model"].isJsonNull).isTrue()
    assertThat(device["apiLevel"].isJsonNull).isTrue()
  }

  // ── data.json tests ─────────────────────────────────────────────────────

  @Test
  fun `test block has className, methodName, status, and timing`() {
    val tr =
      makeTestResult(
        "com.example.LoginTest",
        "testInvalidPassword",
        TestStatus.FAIL,
        exception = "AssertionError: expected true",
      )
    val summary = buildSummary(devices = listOf(makeDeviceResult("emu-1", listOf(tr))))

    renderer.render(summary, outputDir)

    val test =
      dataJson()["devices"].asJsonArray[0].asJsonObject["tests"].asJsonArray[0].asJsonObject
    assertThat(test["className"].asString).isEqualTo("com.example.LoginTest")
    assertThat(test["methodName"].asString).isEqualTo("testInvalidPassword")
    assertThat(test["status"].asString).isEqualTo("FAIL")
    assertThat(test["exception"].asString).contains("AssertionError")
  }

  @Test
  fun `screenshots paths are relative to outputDir`() {
    val shot =
      File(outputDir, "images/emu-1/Foo/bar/001.png").also {
        it.parentFile.mkdirs()
        it.writeText("fake")
      }
    val tr =
      TestResult.builder()
        .testCase(TestCase("Foo", "bar"))
        .status(TestStatus.PASS)
        .startedAt(System.currentTimeMillis())
        .durationMs(100L)
        .screenshot(shot)
        .build()
    renderer.render(
      buildSummary(devices = listOf(makeDeviceResult("emu-1", listOf(tr)))),
      outputDir,
    )

    val shots =
      dataJson()["devices"]
        .asJsonArray[0]
        .asJsonObject["tests"]
        .asJsonArray[0]
        .asJsonObject["screenshots"]
        .asJsonArray
    assertThat(shots[0].asString).isEqualTo("images/emu-1/Foo/bar/001.png")
  }

  @Test
  fun `no video produces null video field`() {
    val tr = makeTestResult("Foo", "bar", TestStatus.PASS)
    renderer.render(
      buildSummary(devices = listOf(makeDeviceResult("emu-1", listOf(tr)))),
      outputDir,
    )

    val test =
      dataJson()["devices"].asJsonArray[0].asJsonObject["tests"].asJsonArray[0].asJsonObject
    assertThat(test["video"].isJsonNull).isTrue()
  }

  // ── index.html ──────────────────────────────────────────────────────────

  @Test
  fun `index html is written`() {
    renderer.render(buildSummary(), outputDir)
    assertThat(File(outputDir, "index.html")).exists()
  }

  @Test
  fun `bundled index html references main js`() {
    renderer.render(buildSummary(), outputDir)
    val html = File(outputDir, "index.html").readText()
    assertThat(html).contains("main.js")
  }

  @Test
  fun `bundled assets are copied next to index html`() {
    renderer.render(buildSummary(), outputDir)
    assertThat(File(outputDir, "main.js")).exists()
  }

  // ── ReportArtifact ──────────────────────────────────────────────────────

  @Test
  fun `returned artifact points to correct files`() {
    val artifact = renderer.render(buildSummary(), outputDir)
    assertThat(artifact.rootDir).isEqualTo(outputDir)
    assertThat(artifact.indexFile.name).isEqualTo("index.html")
  }

  // ── meta executionMode ──────────────────────────────────────────────────

  @Test
  fun `meta block contains executionMode`() {
    renderer.render(buildSummary(), outputDir)
    val meta = dataJson()["meta"].asJsonObject
    assertThat(meta["executionMode"].asString).isEqualTo("DISTRIBUTE")
  }

  // ── ignoredReason ───────────────────────────────────────────────────────

  @Test
  fun `ignoredReason is serialized in test block`() {
    val tr =
      TestResult.builder()
        .testCase(TestCase("com.Foo", "testBar"))
        .status(TestStatus.IGNORED)
        .ignoredReason("Device disconnected during test run")
        .startedAt(System.currentTimeMillis())
        .durationMs(0)
        .build()
    renderer.render(
      buildSummary(devices = listOf(makeDeviceResult("emu-1", listOf(tr)))),
      outputDir,
    )

    val test =
      dataJson()["devices"].asJsonArray[0].asJsonObject["tests"].asJsonArray[0].asJsonObject
    assertThat(test["ignoredReason"].asString).isEqualTo("Device disconnected during test run")
  }

  // ── FANOUT aggregation ──────────────────────────────────────────────────

  @Test
  fun `fanout mode aggregates tests with worst-wins across devices`() {
    val passResult = makeTestResult("com.Foo", "testBar", TestStatus.PASS)
    val failResult = makeTestResult("com.Foo", "testBar", TestStatus.FAIL)
    val summary =
      buildSummary(
        devices =
          listOf(
            makeDeviceResult("emu-1", listOf(passResult)),
            makeDeviceResult("emu-2", listOf(failResult)),
          ),
        executionMode = ExecutionMode.FANOUT,
      )
    renderer.render(summary, outputDir)

    val tests = dataJson()["tests"].asJsonArray
    assertThat(tests.size()).isEqualTo(1)
    val test = tests[0].asJsonObject
    assertThat(test["aggregatedStatus"].asString).isEqualTo("FAIL")
    assertThat(test["deviceStatuses"].asJsonObject["emu-1"].asString).isEqualTo("PASS")
    assertThat(test["deviceStatuses"].asJsonObject["emu-2"].asString).isEqualTo("FAIL")
  }

  @Test
  fun `fanout summary counts are based on aggregated statuses`() {
    val passResult = makeTestResult("com.Foo", "testBar", TestStatus.PASS)
    val failResult = makeTestResult("com.Foo", "testBar", TestStatus.FAIL)
    val summary =
      buildSummary(
        devices =
          listOf(
            makeDeviceResult("emu-1", listOf(passResult)),
            makeDeviceResult("emu-2", listOf(failResult)),
          ),
        executionMode = ExecutionMode.FANOUT,
      )
    renderer.render(summary, outputDir)

    val s = dataJson()["summary"].asJsonObject
    assertThat(s["total"].asInt).isEqualTo(1) // 1 unique test
    assertThat(s["failed"].asInt).isEqualTo(1) // FAIL wins over PASS
    assertThat(s["passed"].asInt).isEqualTo(0)
  }

  @Test
  fun `distribute mode has null tests array`() {
    renderer.render(buildSummary(), outputDir)
    assertThat(dataJson()["tests"].isJsonNull).isTrue()
  }

  @Test
  fun `fanout worst-wins ordering is FAIL over ASSUMPTION_FAILURE over IGNORED over PASS`() {
    val t1 = makeTestResult("com.Foo", "m", TestStatus.PASS)
    val t2 = makeTestResult("com.Foo", "m", TestStatus.ASSUMPTION_FAILURE)
    val summary =
      buildSummary(
        devices =
          listOf(makeDeviceResult("emu-1", listOf(t1)), makeDeviceResult("emu-2", listOf(t2))),
        executionMode = ExecutionMode.FANOUT,
      )
    renderer.render(summary, outputDir)

    val test = dataJson()["tests"].asJsonArray[0].asJsonObject
    assertThat(test["aggregatedStatus"].asString).isEqualTo("ASSUMPTION_FAILURE")
  }

  // ── helpers ─────────────────────────────────────────────────────────────

  private fun dataJson() =
    JsonParser.parseReader(File(outputDir, "data.json").reader()).asJsonObject

  private fun buildSummary(
    title: String? = "Test Suite",
    devices: List<DeviceResult> = emptyList(),
    missed: List<TestCase> = emptyList(),
    executionMode: ExecutionMode = ExecutionMode.DISTRIBUTE,
  ): OrbitSummary {
    val builder =
      OrbitSummary.builder()
        .title(title)
        .startedAt(1_000_000L)
        .durationMs(60_000L)
        .orbitVersion("1.0.0")
        .missedTests(missed)
        .executionMode(executionMode)
    devices.forEach { builder.result(it.serial, it) }
    return builder.build()
  }

  private fun makeDeviceResult(
    serial: String,
    tests: List<TestResult>,
    details: DeviceDetails? = DeviceDetails("TestDevice", "13", 33),
  ): DeviceResult {
    val builder =
      DeviceResult.builder()
        .serial(serial)
        .status(DeviceStatus.COMPLETED)
        .deviceDetails(details)
        .startedAt(1_000_000L)
        .durationMs(30_000L)
    tests.forEach { builder.testResult(it.testCase, it) }
    return builder.build()
  }

  private fun makeTestResult(
    className: String,
    methodName: String,
    status: TestStatus,
    exception: String? = null,
  ): TestResult =
    TestResult.builder()
      .testCase(TestCase(className, methodName))
      .status(status)
      .startedAt(System.currentTimeMillis())
      .durationMs(1000L)
      .exception(exception)
      .build()
}
