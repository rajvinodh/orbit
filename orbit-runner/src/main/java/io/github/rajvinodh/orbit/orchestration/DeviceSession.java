package io.github.rajvinodh.orbit.orchestration;

import com.android.ddmlib.logcat.LogCatMessage;
import io.github.rajvinodh.orbit.collection.OrbitDeviceLogger;
import io.github.rajvinodh.orbit.domain.DeviceDetails;
import io.github.rajvinodh.orbit.domain.DeviceDisconnectionInfo;
import io.github.rajvinodh.orbit.domain.DeviceResult;
import io.github.rajvinodh.orbit.domain.DeviceStatus;
import io.github.rajvinodh.orbit.domain.TestCase;
import io.github.rajvinodh.orbit.domain.TestResult;
import io.github.rajvinodh.orbit.domain.TestStatus;
import io.github.rajvinodh.orbit.listener.MulticastTestRunListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.Getter;

/**
 * Per-device mutable state during a run. Lives from Phase 2 (provision) through Phase 4 (collect).
 * Phase 3 adds TestResults via addTestResult(). Phase 4 adds media (screenshots, videos, files,
 * logcat) via dedicated methods. build() merges everything into an immutable DeviceResult.
 */
public class DeviceSession {

  @Getter private final String serial;
  @Getter private final MulticastTestRunListener multicast;
  @Nullable @Getter private final OrbitDeviceLogger logcatLogger;

  private final long startedAt = System.currentTimeMillis();
  @Nullable private DeviceDetails deviceDetails;

  private DeviceStatus status = DeviceStatus.COMPLETED;
  @Nullable private String provisioningFailureMessage;
  @Nullable private DeviceDisconnectionInfo disconnection;

  // Phase 3 — test execution results
  private final Map<TestCase, TestResult> testResults = new LinkedHashMap<>();
  private final List<String> exceptions = new ArrayList<>();

  // Phase 4 — media buffered per-test, merged in build()
  private final Map<TestCase, List<File>> screenshots = new LinkedHashMap<>();
  private final Map<TestCase, List<File>> videos = new LinkedHashMap<>();
  private final Map<TestCase, List<File>> files = new LinkedHashMap<>();
  private final Map<TestCase, List<LogCatMessage>> logcatMessages = new LinkedHashMap<>();

  public DeviceSession(
      String serial, MulticastTestRunListener multicast, @Nullable OrbitDeviceLogger logcatLogger) {
    this.serial = serial;
    this.multicast = multicast;
    this.logcatLogger = logcatLogger;
  }

  // --- Phase 3 ---

  public void markDisconnected() {
    this.status = DeviceStatus.DISCONNECTED;
    this.disconnection = new DeviceDisconnectionInfo(System.currentTimeMillis());
  }

  public void markProvisioningFailed(String message) {
    this.status = DeviceStatus.PROVISIONING_FAILED;
    this.provisioningFailureMessage = message;
  }

  public void setDeviceDetails(DeviceDetails details) {
    this.deviceDetails = details;
  }

  public void addTestResult(TestCase test, TestResult result) {
    testResults.put(test, result);
  }

  public void addIgnoredTest(TestCase test, String reason) {
    testResults.put(
        test,
        TestResult.builder()
            .testCase(test)
            .status(TestStatus.IGNORED)
            .ignoredReason(reason)
            .startedAt(System.currentTimeMillis())
            .durationMs(0)
            .build());
  }

  public void addException(String message) {
    exceptions.add(message);
  }

  public Map<TestCase, TestResult> getTestResults() {
    return Collections.unmodifiableMap(testResults);
  }

  // --- Phase 4 media ---

  public void addScreenshot(TestCase test, File screenshot) {
    screenshots.computeIfAbsent(test, k -> new ArrayList<>()).add(screenshot);
  }

  public void addVideo(TestCase test, File video) {
    videos.computeIfAbsent(test, k -> new ArrayList<>()).add(video);
  }

  public void addFile(TestCase test, File file) {
    files.computeIfAbsent(test, k -> new ArrayList<>()).add(file);
  }

  public void addLogcat(TestCase test, List<LogCatMessage> messages) {
    logcatMessages.computeIfAbsent(test, k -> new ArrayList<>()).addAll(messages);
  }

  // --- Final assembly ---

  public DeviceResult build() {
    long durationMs = System.currentTimeMillis() - startedAt;

    DeviceResult.DeviceResultBuilder resultBuilder =
        DeviceResult.builder()
            .serial(serial)
            .status(status)
            .deviceDetails(deviceDetails)
            .provisioningFailureMessage(provisioningFailureMessage)
            .startedAt(startedAt)
            .durationMs(durationMs)
            .disconnection(disconnection);

    exceptions.forEach(resultBuilder::exception);

    for (Map.Entry<TestCase, TestResult> entry : testResults.entrySet()) {
      TestCase test = entry.getKey();
      TestResult r = entry.getValue();

      List<File> shots = screenshots.getOrDefault(test, Collections.emptyList());
      List<File> vids = videos.getOrDefault(test, Collections.emptyList());
      List<File> fls = files.getOrDefault(test, Collections.emptyList());
      List<LogCatMessage> logs = logcatMessages.getOrDefault(test, Collections.emptyList());

      TestResult.TestResultBuilder enhanced =
          r.toBuilder().clearScreenshots().clearVideos().clearFiles().clearLogcat().clearCrashLog();
      shots.forEach(enhanced::screenshot);
      vids.forEach(enhanced::video);
      fls.forEach(enhanced::file);
      if (r.isFailed()) {
        logs.forEach(enhanced::logcatMessage);
        logs.stream()
            .filter(m -> "AndroidRuntime".equals(m.getHeader().getTag()))
            .forEach(enhanced::crashMessage);
      }

      resultBuilder.testResult(test, enhanced.build());
    }

    return resultBuilder.build();
  }
}
