package io.github.rajvinodh.orbit.execution

import com.android.ddmlib.IDevice
import com.android.ddmlib.testrunner.ITestRunListener
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner
import io.github.rajvinodh.orbit.domain.TestCase
import io.github.rajvinodh.orbit.domain.TestMetadata
import io.github.rajvinodh.orbit.domain.TestResult
import io.github.rajvinodh.orbit.domain.TestStatus
import io.github.rajvinodh.orbit.execution.actions.PostTestAction
import io.github.rajvinodh.orbit.execution.actions.PreTestAction
import io.github.rajvinodh.orbit.listener.RecentTestOutcomeProvider
import io.github.rajvinodh.orbit.orchestration.DeviceDisconnectedException
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TestExecutionPipelineTest {

  private val device = mockk<IDevice>(relaxed = true)
  private val runner = mockk<RemoteAndroidTestRunner>(relaxed = true)
  private val pre = mockk<PreTestAction>()
  private val post = mockk<PostTestAction>()
  private val outcome = mockk<RecentTestOutcomeProvider>()

  private val ctx =
    TestExecutionContext.builder()
      .testCase(TestCase("com.Foo", "bar"))
      .metadata(TestMetadata.builder().testCase(TestCase("com.Foo", "bar")).build())
      .deviceSerial("emu-1")
      .externalStorage("/sdcard")
      .build()

  private fun passResult() =
    TestResult.builder()
      .testCase(TestCase("com.Foo", "bar"))
      .status(TestStatus.PASS)
      .startedAt(0)
      .durationMs(100)
      .build()

  private fun failResult() =
    TestResult.builder()
      .testCase(TestCase("com.Foo", "bar"))
      .status(TestStatus.FAIL)
      .startedAt(0)
      .durationMs(100)
      .build()

  @BeforeEach
  fun setup() {
    every { pre.before(any(), any()) } just Runs
    every { post.after(any(), any(), any()) } just Runs
    every { outcome.didRecentTestFail() } returns false
    every { outcome.getLastResult() } returns passResult()
  }

  private fun pipeline(retry: RetryPolicy = RetryPolicy.NONE) =
    TestExecutionPipeline(listOf(pre), listOf(post), retry)

  @Test
  fun `preActions called before runner run`() {
    val order = mutableListOf<String>()
    every { pre.before(any(), any()) } answers { order += "pre" }
    every { runner.run(any<Collection<ITestRunListener>>()) } answers { order += "run" }

    pipeline().execute(device, ctx, runner, emptyList(), outcome)

    assertThat(order).containsExactly("pre", "run")
  }

  @Test
  fun `postActions called with failed=false after passing test`() {
    every { outcome.didRecentTestFail() } returns false
    pipeline().execute(device, ctx, runner, emptyList(), outcome)
    verify { post.after(device, any(), false) }
  }

  @Test
  fun `postActions called with failed=true after failing test`() {
    every { outcome.didRecentTestFail() } returns true
    every { outcome.getLastResult() } returns failResult()
    pipeline().execute(device, ctx, runner, emptyList(), outcome)
    verify { post.after(device, any(), true) }
  }

  @Test
  fun `DeviceDisconnectedException propagates from preAction`() {
    every { pre.before(any(), any()) } throws DeviceDisconnectedException("emu-1")
    assertThatThrownBy { pipeline().execute(device, ctx, runner, emptyList(), outcome) }
      .isInstanceOf(DeviceDisconnectedException::class.java)
  }

  @Test
  fun `RetryPolicy NONE results in single execution`() {
    pipeline(RetryPolicy.NONE).execute(device, ctx, runner, emptyList(), outcome)
    verify(exactly = 1) { runner.run(any<Collection<ITestRunListener>>()) }
  }

  @Test
  fun `RetryPolicy retries on fail and stops on pass`() {
    val results = mutableListOf(failResult(), passResult())
    every { outcome.didRecentTestFail() } returnsMany listOf(true, false)
    every { outcome.getLastResult() } answers { results.removeFirst() }

    val maxTwo = RetryPolicy { _, result -> result?.isFailed == true }
    pipeline(maxTwo).execute(device, ctx, runner, emptyList(), outcome)

    verify(exactly = 2) { runner.run(any<Collection<ITestRunListener>>()) }
  }

  @Test
  fun `attemptNumber incremented across retries`() {
    val attempts = mutableListOf<Int>()
    every { pre.before(any(), any()) } answers
      {
        attempts += secondArg<TestExecutionContext>().attemptNumber
      }

    val results = mutableListOf(failResult(), passResult())
    every { outcome.didRecentTestFail() } returnsMany listOf(true, false)
    every { outcome.getLastResult() } answers { results.removeFirst() }

    val maxTwo = RetryPolicy { _, result -> result?.isFailed == true }
    pipeline(maxTwo).execute(device, ctx, runner, emptyList(), outcome)

    assertThat(attempts).containsExactly(0, 1)
  }
}
