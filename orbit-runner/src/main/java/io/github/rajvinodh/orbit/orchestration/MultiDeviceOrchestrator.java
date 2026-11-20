package io.github.rajvinodh.orbit.orchestration;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import io.github.rajvinodh.orbit.collection.CoverageMerger;
import io.github.rajvinodh.orbit.config.RunConfig;
import io.github.rajvinodh.orbit.device.DeviceProvisioner;
import io.github.rajvinodh.orbit.device.ProvisionResult;
import io.github.rajvinodh.orbit.discovery.TestDiscovery;
import io.github.rajvinodh.orbit.domain.DeviceResult;
import io.github.rajvinodh.orbit.domain.DiscoveryResult;
import io.github.rajvinodh.orbit.domain.ExecutionMode;
import io.github.rajvinodh.orbit.domain.OrbitSummary;
import io.github.rajvinodh.orbit.domain.TestCase;
import io.github.rajvinodh.orbit.domain.TestResult;
import io.github.rajvinodh.orbit.domain.TestStatus;
import io.github.rajvinodh.orbit.execution.TestExecutionPipeline;
import io.github.rajvinodh.orbit.execution.actions.AppDataCleaner;
import io.github.rajvinodh.orbit.execution.actions.CoveragePuller;
import io.github.rajvinodh.orbit.execution.actions.LogcatClearer;
import io.github.rajvinodh.orbit.execution.actions.NetworkLogPuller;
import io.github.rajvinodh.orbit.execution.actions.PostTestAction;
import io.github.rajvinodh.orbit.execution.actions.PreTestAction;
import io.github.rajvinodh.orbit.execution.actions.VideoRecorder;
import io.github.rajvinodh.orbit.report.JUnitXmlReportWriter;
import io.github.rajvinodh.orbit.report.OrbitReportRenderer;
import io.github.rajvinodh.orbit.report.ReportArtifact;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

/** Top-level orchestrator. Coordinates all 5 phases across all target devices. */
@Slf4j
public class MultiDeviceOrchestrator {

  private static final String ORBIT_VERSION = "1.0.1";
  private static final DateTimeFormatter TITLE_DATE_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

  private final RunConfig config;
  private final DeviceProvisioner provisioner;
  private final ExecutorService executor;

  public MultiDeviceOrchestrator(RunConfig config, DeviceProvisioner provisioner) {
    this.config = config;
    this.provisioner = provisioner;
    this.executor =
        Executors.newCachedThreadPool(
            r -> {
              Thread t = new Thread(r, "orbit-worker");
              t.setDaemon(true);
              return t;
            });
  }

  public OrbitSummary run(AndroidDebugBridge adb) {
    long startedAt = System.currentTimeMillis();

    try {
      log.debug("Clearing output directory: {}", config.getOutput().getAbsolutePath());
      FileUtils.deleteDirectory(config.getOutput());
    } catch (IOException e) {
      throw new RuntimeException("Unable to clean output directory: " + config.getOutput(), e);
    }

    Set<String> effectiveSerials = resolveSerials(adb);

    if (effectiveSerials.isEmpty()) {
      if (!config.isAllowNoDevices()) {
        throw new IllegalStateException(
            "No devices available — aborting. Connect a device or emulator and retry.");
      }
      log.warn("No devices available — continuing because allowNoDevices=true");
    }

    log.info("Target devices: {}", effectiveSerials);

    // ── Phase 1: Discovery (sequential, one device) ───────────────────
    DiscoveryResult discovery = runDiscovery(effectiveSerials, adb);
    List<TestCase> testsToRun = config.getSelectionStrategy().select(discovery);

    // Log every test that will execute
    int total = testsToRun.size();
    log.info("Tests to run ({})", total);
    for (int i = 0; i < total; i++) {
      log.info("  [{}/{}] {}", i + 1, total, testsToRun.get(i).displayName());
    }

    // Tests in discovery but filtered out by strategy are "missed"
    List<TestCase> missed = new ArrayList<>(discovery.getActiveTests());
    missed.removeAll(testsToRun);

    // ── Phase 2: Provision all devices in parallel ────────────────────
    Map<String, DeviceOrchestrator> orchestrators = provisionAll(effectiveSerials, adb);

    // ── Phase 3: Execute (one DeviceWorker per provisioned device) ────
    ExecutionMode mode = config.getExecutionMode();
    CountDownLatch executionDone = new CountDownLatch(orchestrators.size());
    AtomicInteger globalCompleted = new AtomicInteger(0);
    List<TestCase> queueDrained = new ArrayList<>();

    if (mode == ExecutionMode.FANOUT) {
      int globalTotal = total * orchestrators.size();
      for (DeviceOrchestrator orch : orchestrators.values()) {
        executor.submit(
            new DeviceWorker(
                orch.getDevice(),
                new LinkedBlockingQueue<>(testsToRun),
                orch,
                discovery,
                executionDone,
                globalTotal,
                globalCompleted,
                true));
      }
    } else {
      BlockingQueue<TestCase> sharedQueue = new LinkedBlockingQueue<>(testsToRun);
      for (DeviceOrchestrator orch : orchestrators.values()) {
        executor.submit(
            new DeviceWorker(
                orch.getDevice(),
                sharedQueue,
                orch,
                discovery,
                executionDone,
                total,
                globalCompleted,
                false));
      }
      awaitUninterruptibly(executionDone);
      sharedQueue.drainTo(queueDrained);
    }
    if (mode == ExecutionMode.FANOUT) {
      awaitUninterruptibly(executionDone);
    }

    // ── Phase 4: Collect artifacts in parallel ────────────────────────
    Map<String, DeviceResult> deviceResults = new ConcurrentHashMap<>();
    CountDownLatch collectDone = new CountDownLatch(orchestrators.size());

    for (DeviceOrchestrator orchestrator : orchestrators.values()) {
      executor.submit(
          () -> {
            String serial = orchestrator.getDevice().getSerialNumber();
            try {
              DeviceResult result = orchestrator.collectAndBuild(adb);
              deviceResults.put(serial, result);
            } catch (Exception e) {
              log.error("Artifact collection failed for {}: {}", serial, e.getMessage());
            } finally {
              collectDone.countDown();
            }
          });
    }
    awaitUninterruptibly(collectDone);
    executor.shutdown();

    if (config.isCodeCoverage()) {
      try {
        CoverageMerger.merge(deviceResults.keySet(), new File(config.getOutput(), "coverage"));
      } catch (Exception e) {
        log.error("Coverage merge failed: {}", e.getMessage());
      }
    }

    // DISTRIBUTE: inject queue-drained tests as IGNORED into first device; fall back to missedTests
    if (mode == ExecutionMode.DISTRIBUTE && !queueDrained.isEmpty()) {
      if (!deviceResults.isEmpty()) {
        String firstSerial = deviceResults.keySet().iterator().next();
        DeviceResult first = deviceResults.get(firstSerial);
        DeviceResult.DeviceResultBuilder rb = first.toBuilder();
        for (TestCase tc : queueDrained) {
          rb.testResult(
              tc,
              TestResult.builder()
                  .testCase(tc)
                  .status(TestStatus.IGNORED)
                  .ignoredReason("No device was available to execute this test")
                  .startedAt(first.getStartedAt())
                  .durationMs(0)
                  .build());
        }
        deviceResults.put(firstSerial, rb.build());
      } else {
        missed.addAll(queueDrained);
      }
    }

    long durationMs = System.currentTimeMillis() - startedAt;

    // ── Phase 5: Report ───────────────────────────────────────────────
    String title =
        config.getReportTitle() != null
            ? config.getReportTitle()
            : config.getAppPackage()
                + " · "
                + TITLE_DATE_FMT.format(Instant.ofEpochMilli(startedAt));

    OrbitSummary.OrbitSummaryBuilder summaryBuilder =
        OrbitSummary.builder()
            .title(title)
            .startedAt(startedAt)
            .durationMs(durationMs)
            .missedTests(missed)
            .executionMode(mode)
            .orbitVersion(ORBIT_VERSION);

    deviceResults.forEach(summaryBuilder::result);
    OrbitSummary summary = summaryBuilder.build();

    File reportDir = new File(config.getOutput(), "orbit-report");
    try {
      ReportArtifact artifact = new OrbitReportRenderer().render(summary, reportDir);
      new JUnitXmlReportWriter().write(summary, reportDir);
      config.getReportPublishers().forEach(p -> p.publish(artifact));
      log.info("Report: {}", artifact.getIndexFile().getAbsolutePath());
    } catch (Exception e) {
      log.error("Report generation failed: {}", e.getMessage());
    }

    return summary;
  }

  // ── Phase 1 ───────────────────────────────────────────────────────────

  private DiscoveryResult runDiscovery(Set<String> serials, AndroidDebugBridge adb) {
    for (String serial : serials) {
      IDevice device = findOnlineDevice(adb, serial);
      if (device == null) {
        log.warn("Discovery: device {} not found or offline — skipping", serial);
        continue;
      }
      try {
        log.info("Installing APKs on discovery device {}", serial);
        ProvisionResult pr = provisioner.provision(device, config);
        if (pr != ProvisionResult.SUCCESS) {
          log.warn("Provisioning failed on {}: {} — skipping for discovery", serial, pr);
          continue;
        }
        log.info("Running discovery on {}", serial);
        return new TestDiscovery(config).run(device);
      } catch (Exception e) {
        log.warn("Discovery failed on {}: {} — trying next device", serial, e.getMessage());
      }
    }
    throw new IllegalStateException("Discovery failed on all candidate devices: " + serials);
  }

  // ── Phase 2 ───────────────────────────────────────────────────────────

  private Map<String, DeviceOrchestrator> provisionAll(
      Set<String> serials, AndroidDebugBridge adb) {
    Map<String, DeviceOrchestrator> result = new ConcurrentHashMap<>();
    CountDownLatch latch = new CountDownLatch(serials.size());

    for (String serial : serials) {
      executor.submit(
          () -> {
            try {
              if (config.getSkipSerials().contains(serial)) {
                log.info("Skipping provisioning for {}", serial);
                return;
              }
              IDevice device = findOnlineDevice(adb, serial);
              if (device == null) {
                log.warn("Provision: device {} not found or offline — skipping", serial);
                return;
              }
              log.info("Provisioning {}", serial);
              ProvisionResult pr = provisioner.provision(device, config);
              if (pr == ProvisionResult.SUCCESS) {
                DeviceOrchestrator orchestrator =
                    new DeviceOrchestrator(config, buildPipeline(serial), device);
                result.put(serial, orchestrator);
                log.info("Provisioned {}", serial);
              } else {
                log.error("Provisioning {} failed with result: {}", serial, pr);
              }
            } catch (Exception e) {
              log.error("Exception provisioning {}: {}", serial, e.getMessage());
            } finally {
              latch.countDown();
            }
          });
    }
    awaitUninterruptibly(latch);
    return result;
  }

  private TestExecutionPipeline buildPipeline(String serial) {
    List<PreTestAction> pre = new ArrayList<>();
    List<PostTestAction> post = new ArrayList<>();

    if (config.isRecordVideo()) {
      VideoRecorder recorder = new VideoRecorder(new File(config.getOutput(), "videos/" + serial));
      pre.add(recorder);
      post.add(recorder);
    }
    pre.add(new AppDataCleaner(config.getAppPackage()));
    pre.add(new LogcatClearer());

    if (config.isTrackNetworkLogs()) {
      post.add(new NetworkLogPuller(new File(config.getOutput(), "network")));
    }
    if (config.isCodeCoverage()) {
      post.add(
          new CoveragePuller(new File(config.getOutput(), "coverage"), config.getAppPackage()));
    }

    return new TestExecutionPipeline(pre, post, config.getRetryPolicy());
  }

  // ── helpers ───────────────────────────────────────────────────────────

  private Set<String> resolveSerials(AndroidDebugBridge adb) {
    if (!config.getSerials().isEmpty()) return config.getSerials();
    Set<String> online = new LinkedHashSet<>();
    for (IDevice device : adb.getDevices()) {
      if (device.getState() == IDevice.DeviceState.ONLINE) {
        online.add(device.getSerialNumber());
      }
    }
    return online;
  }

  private IDevice findOnlineDevice(AndroidDebugBridge adb, String serial) {
    for (IDevice device : adb.getDevices()) {
      if (device.getSerialNumber().equals(serial)
          && device.getState() == IDevice.DeviceState.ONLINE) {
        return device;
      }
    }
    return null;
  }

  private void awaitUninterruptibly(CountDownLatch latch) {
    boolean interrupted = false;
    while (true) {
      try {
        latch.await();
        break;
      } catch (InterruptedException e) {
        interrupted = true;
      }
    }
    if (interrupted) Thread.currentThread().interrupt();
  }
}
