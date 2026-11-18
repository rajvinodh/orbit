package io.github.rajvinodh.orbit.orchestration;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import io.github.rajvinodh.orbit.collection.ArtifactCollector;
import io.github.rajvinodh.orbit.collection.CoverageCollector;
import io.github.rajvinodh.orbit.collection.FileProcessor;
import io.github.rajvinodh.orbit.collection.LogcatMapper;
import io.github.rajvinodh.orbit.collection.NetworkLogProcessor;
import io.github.rajvinodh.orbit.collection.OrbitDeviceLogger;
import io.github.rajvinodh.orbit.collection.ScreenshotProcessor;
import io.github.rajvinodh.orbit.collection.VideoProcessor;
import io.github.rajvinodh.orbit.config.RunConfig;
import io.github.rajvinodh.orbit.domain.DeviceResult;
import io.github.rajvinodh.orbit.domain.TestCase;
import io.github.rajvinodh.orbit.domain.TestMetadata;
import io.github.rajvinodh.orbit.domain.TestResult;
import io.github.rajvinodh.orbit.execution.TestExecutionContext;
import io.github.rajvinodh.orbit.execution.TestExecutionPipeline;
import io.github.rajvinodh.orbit.listener.MulticastTestRunListener;
import io.github.rajvinodh.orbit.listener.OrbitTestRunListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages the lifecycle of a single device during a run: session start, per-test execution, session
 * end, and artifact collection. One orchestrator per provisioned device.
 */
@Slf4j
public class DeviceOrchestrator {

  private final RunConfig config;
  private final TestExecutionPipeline pipeline;
  private final IDevice device;

  private volatile DeviceSession completedSession;

  public DeviceOrchestrator(RunConfig config, TestExecutionPipeline pipeline, IDevice device) {
    this.config = config;
    this.pipeline = pipeline;
    this.device = device;
  }

  public IDevice getDevice() {
    return device;
  }

  public DeviceSession startSession(IDevice device) {
    MulticastTestRunListener multicast = new MulticastTestRunListener(config.getTestRunListeners());
    OrbitDeviceLogger logger =
        config.isTrackLogcat()
            ? new OrbitDeviceLogger(device, config.getLogcatLevel(), config.getAppPackage())
            : null;
    DeviceSession session = new DeviceSession(device.getSerialNumber(), multicast, logger);
    multicast.multiRunStarted(config.getAppPackage(), 0);
    return session;
  }

  public @Nullable TestResult execute(
      IDevice device, TestCase test, TestMetadata metadata, DeviceSession session)
      throws Exception {
    TestExecutionContext ctx =
        TestExecutionContext.builder()
            .testCase(test)
            .metadata(metadata)
            .deviceSerial(device.getSerialNumber())
            .externalStorage("/sdcard")
            .build();

    OrbitTestRunListener orbitListener = new OrbitTestRunListener();
    RemoteAndroidTestRunner runner = createRunner(device, test);

    try {
      pipeline.execute(
          device, ctx, runner, List.of(orbitListener, session.getMulticast()), orbitListener);
    } catch (Exception e) {
      if (isDisconnect(e)) {
        throw new DeviceDisconnectedException(device.getSerialNumber(), e);
      }
      throw e;
    }

    TestResult result = orbitListener.getLastResult();
    if (result != null) {
      if (metadata != null && metadata.getExternalId() != null) {
        result = result.toBuilder().externalId(metadata.getExternalId()).build();
      }
      session.addTestResult(test, result);
    }
    if (orbitListener.getRunFailureMessage() != null) {
      session.addException(orbitListener.getRunFailureMessage());
    }
    return result;
  }

  public void endSession(IDevice device, DeviceSession session) {
    try {
      session.getMulticast().multiRunEnded();
    } catch (Exception e) {
      log.warn(
          "Error ending multicast session on {}: {}", device.getSerialNumber(), e.getMessage());
    }
    this.completedSession = session;
  }

  /**
   * Phase 4: run all collectors against the completed session and return the final DeviceResult.
   */
  public DeviceResult collectAndBuild(AndroidDebugBridge adb) throws IOException {
    DeviceSession session = completedSession;
    if (session == null)
      throw new IllegalStateException("endSession() not called before collectAndBuild()");

    String serial = device.getSerialNumber();
    File output = config.getOutput();

    for (ArtifactCollector collector : buildCollectors(serial, output)) {
      try {
        collector.collect(device, session);
      } catch (Exception e) {
        log.error(
            "Collector {} failed on {}: {}",
            collector.getClass().getSimpleName(),
            serial,
            e.getMessage());
      }
    }
    return session.build();
  }

  private List<ArtifactCollector> buildCollectors(String serial, File output) {
    List<ArtifactCollector> collectors = new ArrayList<>();
    collectors.add(new ScreenshotProcessor(new File(output, "images/" + serial)));
    collectors.add(new FileProcessor(new File(output, "files/" + serial)));
    if (config.isRecordVideo())
      collectors.add(new VideoProcessor(new File(output, "videos/" + serial)));
    if (config.isTrackNetworkLogs())
      collectors.add(new NetworkLogProcessor(new File(output, "network/" + serial)));
    if (config.isTrackLogcat()) collectors.add(new LogcatMapper());
    if (config.isCodeCoverage()) {
      collectors.add(new CoverageCollector(new File(output, "coverage")));
    }
    return collectors;
  }

  private RemoteAndroidTestRunner createRunner(IDevice device, TestCase test) {
    RemoteAndroidTestRunner runner =
        new RemoteAndroidTestRunner(config.effectiveTestPackage(), config.getTestRunner(), device);
    runner.setMaxTimeToOutputResponse(
        config.getAdbTimeout().toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
    runner.setMethodName(test.getClassName(), test.getMethodName());
    config.getInstrumentationArgs().forEach(runner::addInstrumentationArg);
    if (config.isCodeCoverage()) {
      String testId = test.getClassName().replace('.', '_') + "_" + test.getMethodName();
      runner.addInstrumentationArg("coverage", "true");
      // No coverageFile arg — AndroidJUnitRunner defaults to
      // /data/data/<instrumentationPackage>/files/coverage.ec which is always writable.
      // /sdcard/ root is blocked by scoped storage (API 29+).
    }
    return runner;
  }

  private boolean isDisconnect(Exception e) {
    String msg = e.getMessage();
    return msg != null
        && (msg.contains("device offline")
            || msg.contains("device not found")
            || msg.contains("Connection refused")
            || e.getClass().getSimpleName().contains("Timeout"));
  }
}
