package io.github.rajvinodh.orbit.collection

import com.android.ddmlib.IDevice
import io.github.rajvinodh.orbit.domain.TestCase
import io.github.rajvinodh.orbit.domain.TestResult
import io.github.rajvinodh.orbit.domain.TestStatus
import io.github.rajvinodh.orbit.listener.MulticastTestRunListener
import io.github.rajvinodh.orbit.orchestration.DeviceSession
import io.mockk.every
import io.mockk.mockk
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class VideoProcessorTest {

  @TempDir lateinit var tempDir: File

  private val device = mockk<IDevice>(relaxed = true)
  private val serial = "emulator-5554"

  private val failTest = TestCase("com.example.FooTest", "testFail")
  private val passTest = TestCase("com.example.FooTest", "testPass")

  private lateinit var session: DeviceSession
  private lateinit var videoDir: File

  @BeforeEach
  fun setUp() {
    every { device.serialNumber } returns serial

    session = DeviceSession(serial, mockk<MulticastTestRunListener>(relaxed = true), null)
    session.addTestResult(
      failTest,
      TestResult.builder().testCase(failTest).status(TestStatus.FAIL).durationMs(1000L).build(),
    )
    session.addTestResult(
      passTest,
      TestResult.builder().testCase(passTest).status(TestStatus.PASS).durationMs(500L).build(),
    )

    videoDir = File(tempDir, "videos/$serial").also { it.mkdirs() }
  }

  // ── parseTestCase ──────────────────────────────────────────────────────

  @Test
  fun `parseTestCase returns correct TestCase for valid filename`() {
    val tc = VideoProcessor.parseTestCase("com.example.FooTest-testFoo-1712345678.mp4")
    assertThat(tc).isNotNull
    assertThat(tc!!.className).isEqualTo("com.example.FooTest")
    assertThat(tc.methodName).isEqualTo("testFoo")
  }

  @Test
  fun `parseTestCase returns null for malformed filename`() {
    assertThat(VideoProcessor.parseTestCase("noTimestamp.mp4")).isNull()
    assertThat(VideoProcessor.parseTestCase("something.avi")).isNull()
    assertThat(VideoProcessor.parseTestCase("justtext")).isNull()
  }

  // ── collect ────────────────────────────────────────────────────────────

  @Test
  fun `video for failed test is associated with session`() {
    val ts = System.currentTimeMillis()
    val mp4 = createVideo(videoDir, "com.example.FooTest-testFail-$ts.mp4")

    VideoProcessor(videoDir).collect(device, session)

    val result = session.build().testResults[failTest]!!
    assertThat(result.videos).hasSize(1)
    assertThat(result.videos[0].name).isEqualTo(mp4.name)
    assertThat(mp4).exists() // file stays in place
  }

  @Test
  fun `video for passed test is not associated`() {
    val ts = System.currentTimeMillis()
    createVideo(videoDir, "com.example.FooTest-testPass-$ts.mp4")

    VideoProcessor(videoDir).collect(device, session)

    assertThat(session.build().testResults[passTest]!!.videos).isEmpty()
  }

  @Test
  fun `malformed video filename is skipped without throwing`() {
    createVideo(videoDir, "bad-name.mp4")

    VideoProcessor(videoDir).collect(device, session)

    session.build().testResults.values.forEach { assertThat(it.videos).isEmpty() }
  }

  @Test
  fun `missing video directory is handled gracefully`() {
    VideoProcessor(File(tempDir, "nonexistent")).collect(device, session)
    session.build().testResults.values.forEach { assertThat(it.videos).isEmpty() }
  }

  // ── helpers ────────────────────────────────────────────────────────────

  private fun createVideo(dir: File, name: String): File =
    File(dir, name).also { it.writeText("fake-mp4") }
}
