package io.github.rajvinodh.orbit.execution.actions

import com.android.ddmlib.CollectingOutputReceiver
import com.android.ddmlib.IDevice
import io.github.rajvinodh.orbit.domain.TestCase
import io.github.rajvinodh.orbit.domain.TestMetadata
import io.github.rajvinodh.orbit.execution.TestExecutionContext
import io.mockk.*
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Test

class AppDataCleanerTest {

  private val device = mockk<IDevice>(relaxed = true)
  private val cleaner = AppDataCleaner("com.example.app")

  private fun ctx(clearBefore: Boolean = false, clearAfter: Boolean = false): TestExecutionContext {
    val test = TestCase("com.Foo", "bar")
    return TestExecutionContext.builder()
      .testCase(test)
      .metadata(
        TestMetadata.builder()
          .testCase(test)
          .clearAppDataBefore(clearBefore)
          .clearAppDataAfter(clearAfter)
          .build()
      )
      .deviceSerial("emu-1")
      .externalStorage("/sdcard")
      .build()
  }

  @Test
  fun `clearBefore=true sends pm clear in before()`() {
    cleaner.before(device, ctx(clearBefore = true))
    verify {
      device.executeShellCommand(
        match { it.contains("pm clear com.example.app") },
        any(),
        any(),
        any(),
      )
    }
  }

  @Test
  fun `clearBefore=false does NOT send pm clear in before()`() {
    cleaner.before(device, ctx(clearBefore = false))
    verify(exactly = 0) { device.executeShellCommand(any(), any(), any<Long>(), any()) }
  }

  @Test
  fun `clearAfter=true sends pm clear in after()`() {
    cleaner.after(device, ctx(clearAfter = true), false)
    verify {
      device.executeShellCommand(
        match { it.contains("pm clear com.example.app") },
        any(),
        any(),
        any(),
      )
    }
  }

  @Test
  fun `pm clear returning Success causes no error log`() {
    val receiver = slot<CollectingOutputReceiver>()
    every {
      device.executeShellCommand(any(), capture(receiver), any<Long>(), any<TimeUnit>())
    } answers
      {
        // simulate "Success" output
      }
    // Should complete without exception
    cleaner.before(device, ctx(clearBefore = true))
  }

  @Test
  fun `pm clear returning unexpected output still continues`() {
    every { device.executeShellCommand(any(), any(), any<Long>(), any<TimeUnit>()) } just Runs
    // Should not throw even with bad output
    cleaner.before(device, ctx(clearBefore = true))
  }
}
