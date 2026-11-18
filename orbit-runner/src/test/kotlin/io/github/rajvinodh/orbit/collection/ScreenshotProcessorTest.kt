package io.github.rajvinodh.orbit.collection

import com.android.ddmlib.IDevice
import io.github.rajvinodh.orbit.domain.TestCase
import io.github.rajvinodh.orbit.domain.TestResult
import io.github.rajvinodh.orbit.domain.TestStatus
import io.github.rajvinodh.orbit.listener.MulticastTestRunListener
import io.github.rajvinodh.orbit.orchestration.DeviceSession
import io.mockk.every
import io.mockk.mockk
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ScreenshotProcessorTest {

  @TempDir lateinit var tempDir: File

  private val device = mockk<IDevice>(relaxed = true)
  private val serial = "emulator-5554"

  private val knownTest = TestCase("com.example.FooTest", "testMethod")
  private val unknownTest = TestCase("com.example.OtherTest", "testOther")

  private lateinit var session: DeviceSession
  private lateinit var imageDir: File

  @BeforeEach
  fun setUp() {
    every { device.serialNumber } returns serial

    session = DeviceSession(serial, mockk<MulticastTestRunListener>(relaxed = true), null)
    val result =
      TestResult.builder().testCase(knownTest).status(TestStatus.FAIL).durationMs(1200L).build()
    session.addTestResult(knownTest, result)

    imageDir = File(tempDir, "images/$serial")
    imageDir.mkdirs()
  }

  @Test
  fun `single screenshot is associated`() {
    createDeviceScreenshots(knownTest, count = 1)

    ScreenshotProcessor(imageDir).collect(device, session)

    val result = session.build().testResults[knownTest]!!
    assertThat(result.screenshots).hasSize(1)
  }

  @Test
  fun `multiple screenshots are all associated`() {
    createDeviceScreenshots(knownTest, count = 3)

    ScreenshotProcessor(imageDir).collect(device, session)

    val result = session.build().testResults[knownTest]!!
    assertThat(result.screenshots).hasSize(3)
  }

  @Test
  fun `screenshot dir for unknown test is skipped`() {
    createDeviceScreenshots(unknownTest, count = 1)

    ScreenshotProcessor(imageDir).collect(device, session)

    val result = session.build().testResults[knownTest]!!
    assertThat(result.screenshots).isEmpty()
  }

  @Test
  fun `empty pull dir is handled gracefully`() {
    File(imageDir, ".pull-tmp").mkdirs()

    ScreenshotProcessor(imageDir).collect(device, session)

    val result = session.build().testResults[knownTest]!!
    assertThat(result.screenshots).isEmpty()
  }

  @Test
  fun `device pull failure does not prevent processing remaining state`() {
    every { device.pullFile(any(), any()) } throws RuntimeException("adb error")

    ScreenshotProcessor(imageDir).collect(device, session)

    val result = session.build().testResults[knownTest]!!
    assertThat(result.screenshots).isEmpty()
  }

  private fun createDeviceScreenshots(test: TestCase, count: Int) {
    val pullRoot =
      File(imageDir, ".pull-tmp/app_orbit-screenshots/${test.className}/${test.methodName}")
    pullRoot.mkdirs()
    repeat(count) { i ->
      val f = File(pullRoot, "${String.format("%03d", i)}.png")
      val img = BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB)
      ImageIO.write(img, "png", f)
    }
  }
}
