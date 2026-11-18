package io.github.rajvinodh.orbit.orchestration;

import com.android.ddmlib.IDevice;
import io.github.rajvinodh.orbit.domain.DiscoveryResult;
import io.github.rajvinodh.orbit.domain.TestCase;
import io.github.rajvinodh.orbit.domain.TestResult;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DeviceWorker implements Runnable {

  private final IDevice device;
  private final BlockingQueue<TestCase> queue;
  private final DeviceOrchestrator orchestrator;
  private final DiscoveryResult discovery;
  private final CountDownLatch doneLatch;
  private final int globalTotal;
  private final AtomicInteger globalCompleted;
  private final boolean drainQueueOnDisconnect;
  private final int deviceTotal;
  private final AtomicInteger deviceCompleted;

  public DeviceWorker(
      IDevice device,
      BlockingQueue<TestCase> queue,
      DeviceOrchestrator orchestrator,
      DiscoveryResult discovery,
      CountDownLatch doneLatch,
      int globalTotal,
      AtomicInteger globalCompleted,
      boolean drainQueueOnDisconnect) {
    this.device = device;
    this.queue = queue;
    this.orchestrator = orchestrator;
    this.discovery = discovery;
    this.doneLatch = doneLatch;
    this.globalTotal = globalTotal;
    this.globalCompleted = globalCompleted;
    this.drainQueueOnDisconnect = drainQueueOnDisconnect;
    this.deviceTotal = queue.size();
    this.deviceCompleted = new AtomicInteger(0);
  }

  @Override
  public void run() {
    DeviceSession session = orchestrator.startSession(device);
    try {
      TestCase test;
      while ((test = queue.poll()) != null) {
        try {
          TestResult result =
              orchestrator.execute(device, test, discovery.getMetadata().get(test), session);
          logCompletion(test, result);
        } catch (DeviceDisconnectedException e) {
          session.markDisconnected();
          if (drainQueueOnDisconnect) {
            session.addIgnoredTest(test, "Device disconnected during test run");
            TestCase remaining;
            while ((remaining = queue.poll()) != null) {
              session.addIgnoredTest(remaining, "Device disconnected during test run");
            }
          }
          return;
        } catch (Exception e) {
          log.error(
              "Unexpected error running {} on {}: {}",
              test.displayName(),
              device.getSerialNumber(),
              e.getMessage());
          session.addException(e.getMessage());
          logCompletion(test, null);
        }
      }
    } finally {
      orchestrator.endSession(device, session);
      doneLatch.countDown();
    }
  }

  private void logCompletion(TestCase test, TestResult result) {
    int gDone = globalCompleted.incrementAndGet();
    int dDone = deviceCompleted.incrementAndGet();
    String status = result != null ? result.getStatus().name() : "ERROR";
    long durationMs = result != null ? result.getDurationMs() : 0;
    log.info(
        "[{}/{}] [{}: {}/{}] {} {} ({}ms)",
        gDone,
        globalTotal,
        device.getSerialNumber(),
        dDone,
        deviceTotal,
        status,
        test.displayName(),
        durationMs);
  }
}
