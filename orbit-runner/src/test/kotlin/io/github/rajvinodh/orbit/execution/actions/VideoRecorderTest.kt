package io.github.rajvinodh.orbit.execution.actions

import com.android.ddmlib.IDevice
import io.github.rajvinodh.orbit.domain.TestCase
import io.github.rajvinodh.orbit.domain.TestMetadata
import io.github.rajvinodh.orbit.execution.TestExecutionContext
import io.mockk.*
import java.io.File
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir

class VideoRecorderTest {

  @TempDir lateinit var tempDir: File

  private val device = mockk<IDevice>(relaxed = true)

  private lateinit var recorder: VideoRecorder
  private lateinit var mockProcess: Process

  @BeforeEach
  fun setUp() {
    recorder = spyk(VideoRecorder(tempDir))
    mockProcess =
      mockk(relaxed = true) {
        every { inputStream } returns
          "INFO: Recording started to mp4 file: test.mp4\n".byteInputStream()
      }
    every { recorder.startProcess(any(), any()) } returns mockProcess
  }

  private fun ctx(serial: String = "emu-1"): TestExecutionContext {
    val test = TestCase("com.Foo", "bar")
    return TestExecutionContext.builder()
      .testCase(test)
      .metadata(TestMetadata.builder().testCase(test).build())
      .deviceSerial(serial)
      .externalStorage("/sdcard")
      .build()
  }
  //
  //    @Test fun `before() starts scrcpy with correct serial and output path`() {
  //        recorder.before(device, ctx())
  //        verify { recorder.startProcess("emu-1", match { it.parentFile == tempDir &&
  // it.name.endsWith(".mp4") }) }
  //    }
  //
  //    @Test fun `after(failed=true) destroys process and keeps video file`() {
  //        val ctx = ctx()
  //        recorder.before(device, ctx)
  //
  //        val videoFile = slot<File>()
  //        verify { recorder.startProcess(any(), capture(videoFile)) }
  //        videoFile.captured.writeText("fake")
  //
  //        recorder.after(device, ctx, true)
  //
  //        verify { mockProcess.destroy() }
  //        assertThat(videoFile.captured).exists()
  //    }
  //
  //    @Test fun `after(failed=false) destroys process and deletes video file`() {
  //        val ctx = ctx()
  //        recorder.before(device, ctx)
  //
  //        val videoFile = slot<File>()
  //        verify { recorder.startProcess(any(), capture(videoFile)) }
  //        videoFile.captured.writeText("fake")
  //
  //        recorder.after(device, ctx, false)
  //
  //        verify { mockProcess.destroy() }
  //        assertThat(videoFile.captured).doesNotExist()
  //    }
  //
  //    @Test fun `after() with no active recording does not throw`() {
  //        recorder.after(device, ctx(), true)   // no matching before() — should just warn
  //    }
  //
  //    @Test fun `buildFilename includes className and methodName`() {
  //        val filename = VideoRecorder.buildFilename(TestCase("com.Foo", "bar"))
  //        assertThat(filename).startsWith("com.Foo-bar-").endsWith(".mp4")
  //    }
}
