package io.github.rajvinodh.orbit.orchestration

import com.android.ddmlib.IDevice
import io.github.rajvinodh.orbit.domain.DiscoveryResult
import io.github.rajvinodh.orbit.domain.TestCase
import io.github.rajvinodh.orbit.domain.TestMetadata
import io.mockk.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicInteger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DeviceWorkerTest {

  private val device = mockk<IDevice>(relaxed = true)
  private val orchestrator = mockk<DeviceOrchestrator>(relaxed = true)
  private val session = mockk<DeviceSession>(relaxed = true)

  private val t1 = TestCase("com.Foo", "a")
  private val t2 = TestCase("com.Foo", "b")
  private val meta = TestMetadata.builder().testCase(t1).build()

  private val discovery =
    DiscoveryResult.builder()
      .activeTests(listOf(t1, t2))
      .metadataEntry(t1, meta)
      .metadataEntry(t2, TestMetadata.builder().testCase(t2).build())
      .build()

  @BeforeEach
  fun setup() {
    every { orchestrator.startSession(device) } returns session
    every { device.getSerialNumber() } returns "emu-1"
  }

  @Test
  fun `drains full queue when device healthy`() {
    val queue = LinkedBlockingQueue(listOf(t1, t2))
    val latch = CountDownLatch(1)
    worker(queue, latch).run()
    assertThat(queue).isEmpty()
    verify(exactly = 2) { orchestrator.execute(device, any(), any(), session) }
    verify { orchestrator.endSession(device, session) }
    assertThat(latch.count).isEqualTo(0)
  }

  @Test
  fun `DeviceDisconnectedException marks session disconnected and exits`() {
    val queue = LinkedBlockingQueue(listOf(t1, t2))
    every { orchestrator.execute(device, t1, any(), session) } throws
      DeviceDisconnectedException("emu-1")
    val latch = CountDownLatch(1)
    worker(queue, latch).run()

    verify { session.markDisconnected() }
    verify(exactly = 1) { orchestrator.execute(device, any(), any(), session) }
    // t2 stays in queue for other workers in DISTRIBUTE mode
    assertThat(queue).containsExactly(t2)
    assertThat(latch.count).isEqualTo(0)
  }

  @Test
  fun `remaining tests stay in queue after disconnect in distribute mode`() {
    val queue = LinkedBlockingQueue(listOf(t1, t2))
    every { orchestrator.execute(device, t1, any(), session) } throws
      DeviceDisconnectedException("emu-1")
    worker(queue, CountDownLatch(1)).run()
    assertThat(queue).containsExactly(t2)
  }

  @Test
  fun `fanout disconnect drains queue and marks all pending tests as IGNORED`() {
    val queue = LinkedBlockingQueue(listOf(t1, t2))
    every { orchestrator.execute(device, t1, any(), session) } throws
      DeviceDisconnectedException("emu-1")
    val latch = CountDownLatch(1)
    worker(queue, latch, drainQueueOnDisconnect = true).run()

    verify { session.markDisconnected() }
    // t1 (the in-flight test) and t2 (remaining in queue) both become IGNORED
    verify { session.addIgnoredTest(t1, any()) }
    verify { session.addIgnoredTest(t2, any()) }
    assertThat(queue).isEmpty()
    assertThat(latch.count).isEqualTo(0)
  }

  @Test
  fun `queue poll timeout exits worker cleanly`() {
    val queue = LinkedBlockingQueue<TestCase>() // empty — poll will timeout
    val latch = CountDownLatch(1)
    worker(queue, latch).run()
    verify { orchestrator.endSession(device, session) }
    assertThat(latch.count).isEqualTo(0)
  }

  @Test
  fun `non-disconnect exception logs and continues to next test`() {
    val queue = LinkedBlockingQueue(listOf(t1, t2))
    every { orchestrator.execute(device, t1, any(), session) } throws RuntimeException("flaky")
    val latch = CountDownLatch(1)
    worker(queue, latch).run()
    // t2 should still have been executed
    verify { orchestrator.execute(device, t2, any(), session) }
    verify(exactly = 0) { session.markDisconnected() }
    assertThat(latch.count).isEqualTo(0)
  }

  // --- helper ---

  private fun worker(
    queue: LinkedBlockingQueue<TestCase>,
    latch: CountDownLatch,
    globalTotal: Int = queue.size,
    globalCompleted: AtomicInteger = AtomicInteger(0),
    drainQueueOnDisconnect: Boolean = false,
  ) =
    DeviceWorker(
      device,
      queue,
      orchestrator,
      discovery,
      latch,
      globalTotal,
      globalCompleted,
      drainQueueOnDisconnect,
    )
}
