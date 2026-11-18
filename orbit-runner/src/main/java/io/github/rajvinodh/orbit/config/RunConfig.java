package io.github.rajvinodh.orbit.config;

import com.android.ddmlib.Log;
import com.android.ddmlib.testrunner.ITestRunListener;
import io.github.rajvinodh.orbit.domain.ExecutionMode;
import io.github.rajvinodh.orbit.execution.RetryPolicy;
import io.github.rajvinodh.orbit.report.FileSystemPublisher;
import io.github.rajvinodh.orbit.report.ReportPublisher;
import io.github.rajvinodh.orbit.selection.TestSelectionStrategy;
import java.io.File;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class RunConfig {

  // --- APKs ---
  File testApk;
  @Singular List<File> otherApks;
  // package of the app under test — needed for pm grant. When null, OrbitRunner infers it from the
  // test APK's <instrumentation android:targetPackage> (see ApkManifestReader).
  @Nullable String appPackage;
  // instrumentation package; inferred from the test APK when null (falls back to appPackage)
  @Nullable String testPackage;
  @Builder.Default String testRunner = ApkManifestReader.DEFAULT_TEST_RUNNER;

  public String effectiveTestPackage() {
    return testPackage != null ? testPackage : appPackage;
  }

  /** Maps a logcat level letter (V/D/I/W/E) to its ddmlib priority; unknown values map to INFO. */
  public static int logcatPriority(String level) {
    return switch (level == null ? "I" : level.trim().toUpperCase()) {
      case "V" -> Log.LogLevel.VERBOSE.getPriority();
      case "D" -> Log.LogLevel.DEBUG.getPriority();
      case "W" -> Log.LogLevel.WARN.getPriority();
      case "E" -> Log.LogLevel.ERROR.getPriority();
      default -> Log.LogLevel.INFO.getPriority();
    };
  }

  // --- Devices ---
  @Singular Set<String> serials;
  @Singular Set<String> skipSerials;
  @Builder.Default boolean allowNoDevices = false;

  // --- Timing ---
  @Builder.Default Duration adbTimeout = Duration.ofMinutes(10);
  @Builder.Default long availabilityTimeoutMin = 30L;

  // --- Test scope ---
  @Nullable String className;
  @Nullable String methodName;
  @Builder.Default Map<String, String> instrumentationArgs = Collections.emptyMap();

  // --- Execution ---
  @Builder.Default boolean grantAll = false;
  @Builder.Default boolean clearAppDataBeforeEachTest = false;
  @Builder.Default boolean codeCoverage = false;

  // --- Observability ---
  @Builder.Default boolean trackLogcat = false;
  @Builder.Default int logcatLevel = Log.LogLevel.INFO.getPriority();
  @Builder.Default boolean recordVideo = false;
  @Builder.Default @Nullable String appiumSettingsVersion = "7.0.24";
  @Builder.Default boolean trackNetworkLogs = false;

  // --- Execution mode ---
  @Builder.Default ExecutionMode executionMode = ExecutionMode.DISTRIBUTE;

  // --- Test selection ---
  @Builder.Default TestSelectionStrategy selectionStrategy = TestSelectionStrategy.ALL;

  // --- Output ---
  @Builder.Default File output = new File("orbit-output");
  @Nullable File androidSdk;
  @Nullable String reportTitle;

  // --- Report publishing ---
  @Builder.Default List<ReportPublisher> reportPublishers = List.of(new FileSystemPublisher());

  // --- External listeners ---
  @Singular List<ITestRunListener> testRunListeners;

  // --- Retry ---
  @Builder.Default RetryPolicy retryPolicy = RetryPolicy.NONE;

  // --- Debug ---
  @Builder.Default boolean debug = false;
}
