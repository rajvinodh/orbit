package io.github.rajvinodh.orbit;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.android.ddmlib.AndroidDebugBridge;
import io.github.rajvinodh.orbit.config.ApkManifestReader;
import io.github.rajvinodh.orbit.config.RunConfig;
import io.github.rajvinodh.orbit.device.DefaultDeviceProvisioner;
import io.github.rajvinodh.orbit.device.DeviceConnectionChecker;
import io.github.rajvinodh.orbit.domain.DeviceResult;
import io.github.rajvinodh.orbit.domain.OrbitSummary;
import io.github.rajvinodh.orbit.domain.TestResult;
import io.github.rajvinodh.orbit.domain.TestStatus;
import io.github.rajvinodh.orbit.orchestration.MultiDeviceOrchestrator;
import java.io.File;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;

/**
 * Entry point for a single Orbit run. Initialises ADB, delegates to MultiDeviceOrchestrator, tears
 * down ADB, and returns whether all tests passed.
 */
@Slf4j
public class OrbitRunner {

  private static final long ADB_INIT_TIMEOUT_MS = 5_000;

  private final RunConfig config;

  public OrbitRunner(RunConfig config) {
    this.config = config;
  }

  /**
   * @return true if all tests passed, false if any failed or errors occurred
   */
  public boolean run() {
    // Only adjust the level when logback is the active SLF4J binding. When embedded in a host
    // that brings its own binding (e.g. Gradle), the host controls log levels.
    if (LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) instanceof Logger root) {
      root.setLevel(config.isDebug() ? Level.DEBUG : Level.INFO);
    }

    // Callers may supply only APK paths — infer app/test package and runner from the test APK.
    RunConfig config = ApkManifestReader.fillMissing(this.config);
    if (config.getAppPackage() == null) {
      log.error(
          "App package unknown: it could not be read from {} — pass --app-package",
          config.getTestApk());
      return false;
    }

    AndroidDebugBridge adb = initAdb();
    if (adb == null) {
      log.error("Failed to initialise ADB — aborting");
      return false;
    }

    try {
      DeviceConnectionChecker checker = new DeviceConnectionChecker();
      DefaultDeviceProvisioner provisioner = new DefaultDeviceProvisioner(adb, checker);
      MultiDeviceOrchestrator orchestrator = new MultiDeviceOrchestrator(config, provisioner);

      OrbitSummary summary = orchestrator.run(adb);
      logSummary(summary);
      return allPassed(summary);
    } catch (Exception e) {
      log.error("Orbit run failed: {}", e.getMessage(), e);
      return false;
    } finally {
      AndroidDebugBridge.terminate();
      log.debug("ADB terminated");
    }
  }

  // ── ADB lifecycle ─────────────────────────────────────────────────────

  private AndroidDebugBridge initAdb() {
    try {
      AndroidDebugBridge.init(false);
      String adbPath = resolveAdbPath();
      log.debug("Connecting to ADB at {}", adbPath);
      AndroidDebugBridge adb = AndroidDebugBridge.createBridge(adbPath, false);

      long deadline = System.currentTimeMillis() + ADB_INIT_TIMEOUT_MS;
      while (!adb.isConnected() && System.currentTimeMillis() < deadline) {
        TimeUnit.MILLISECONDS.sleep(200);
      }

      if (!adb.isConnected()) {
        log.error("ADB did not connect within {}ms", ADB_INIT_TIMEOUT_MS);
        return null;
      }

      // Wait for device list to populate
      deadline = System.currentTimeMillis() + ADB_INIT_TIMEOUT_MS;
      while (!adb.hasInitialDeviceList() && System.currentTimeMillis() < deadline) {
        TimeUnit.MILLISECONDS.sleep(200);
      }

      return adb;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return null;
    } catch (Exception e) {
      log.error("ADB init error: {}", e.getMessage());
      return null;
    }
  }

  private String resolveAdbPath() {
    File sdk = config.getAndroidSdk();
    if (sdk != null) {
      return new File(sdk, "platform-tools" + File.separator + "adb").getAbsolutePath();
    }
    String androidHome = System.getenv("ANDROID_HOME");
    if (androidHome == null) androidHome = System.getenv("ANDROID_SDK_ROOT");
    if (androidHome != null) {
      return new File(androidHome, "platform-tools" + File.separator + "adb").getAbsolutePath();
    }
    return "adb"; // fall back to PATH
  }

  // ── result helpers ────────────────────────────────────────────────────

  private boolean allPassed(OrbitSummary summary) {
    if (!summary.getMissedTests().isEmpty()) return false;
    for (DeviceResult dr : summary.getResults().values()) {
      for (TestResult tr : dr.getTestResults().values()) {
        if (tr.getStatus() == TestStatus.FAIL) return false;
      }
    }
    return true;
  }

  private void logSummary(OrbitSummary summary) {
    int total = 0, passed = 0, failed = 0, ignored = 0;
    for (DeviceResult dr : summary.getResults().values()) {
      for (TestResult tr : dr.getTestResults().values()) {
        total++;
        switch (tr.getStatus()) {
          case PASS -> passed++;
          case FAIL -> failed++;
          case IGNORED, ASSUMPTION_FAILURE -> ignored++;
        }
      }
    }
    total += summary.getMissedTests().size();
    ignored += summary.getMissedTests().size();

    log.info("─────────────────────────────────────────");
    log.info("  Orbit run complete: {}", summary.getTitle());
    log.info("  Total: {}  Passed: {}  Failed: {}  Ignored: {}", total, passed, failed, ignored);
    log.info("  Duration: {}s", summary.getDurationMs() / 1000);
    log.info("─────────────────────────────────────────");
  }
}
