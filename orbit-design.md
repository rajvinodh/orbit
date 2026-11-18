# Orbit — Design Document

> Android multi-device test orchestration framework.  
> Clean rewrite of the phonepeExecution path following SOLID principles, Lombok, and a BlockingQueue-based scheduler.

---

## Table of Contents

1. [Scope](#1-scope)
2. [Project Structure](#2-project-structure)
3. [Domain Model](#3-domain-model)
4. [Configuration — RunConfig](#4-configuration--runconfig)
5. [Extension Points](#5-extension-points)
6. [Discovery](#6-discovery)
7. [Device Provisioning](#7-device-provisioning)
8. [Execution Pipeline](#8-execution-pipeline)
9. [Multi-Device Orchestration](#9-multi-device-orchestration)
10. [Artifact Collection](#10-artifact-collection)
11. [Listeners](#11-listeners)
12. [Report](#12-report)
13. [CLI](#13-cli)
14. [Testing Strategy](#14-testing-strategy)
15. [Dependency Decisions](#15-dependency-decisions)
16. [Gradle Plugin](#16-gradle-plugin)
17. [Contribution Guide](CONTRIBUTION_GUIDE.md)

---

## 1. Scope

### Kept (phonepeExecution path only)

| Feature | Notes |
|---|---|
| Multi-device parallel execution | BlockingQueue scheduler |
| Per-device APK install + provisioning | |
| Per-test video recording | Start/stop/poll/delete if passed; scrcpy only |
| Logcat tracking | Capture + map to failed tests only |
| Screenshot collection | Pull and associate with test result |
| Arbitrary file collection (orbit-files) | |
| JUnit XML output | `orbit-report/junit/TEST-{serial}.xml` per device; CI-compatible |
| ClearAppData per test | BEFORE / AFTER / BOTH |
| ClearLogCat per test | |
| Network log collection | Chucker DB |
| Code coverage | Per-test pull + final merge |
| External test ID | Generic `@ExternalId`, TMS-agnostic |
| Test selection by external ID | Pluggable `TestSelectionStrategy` |
| Device disconnection handling | Full lifecycle — provision + execution |
| HTML report | Immersive SPA |
| Report publishing | Pluggable `ReportPublisher` |

### Dropped

- Allure
- Class-level execution
- Dependent tests (`@DependencyMethod` / `@DependeeMethod`)
- Device eligibility checker
- TAP live status update
- `retryTimes` (designed for easy future addition via `RetryPolicy`)
- `initScript`
- `noAnimations` — animated GIF generation removed entirely
- `mapLogcatForAll` — when `trackLogcat=true`, logcat always mapped to failed tests only
- `executionType` / `testSource` — replaced by `TestSelectionStrategy`
- `shard`, `singleInstrumentationCall`, `sequential`
- TestRail-specific ID — replaced by generic `externalId`

---

## 2. Project Structure

```
orbit/
├── orbit-runner/                        ← main module (Kotlin + Java)
│   └── src/
│       ├── main/kotlin/com/phonepe/orbit/
│       │   ├── cli/
│       │   │   └── OrbitCommand.kt
│       │   ├── config/
│       │   │   └── RunConfig.java
│       │   ├── domain/
│       │   │   ├── TestCase.java
│       │   │   ├── TestStatus.java
│       │   │   ├── TestResult.java
│       │   │   ├── DeviceStatus.java
│       │   │   ├── DeviceResult.java
│       │   │   ├── DeviceDisconnectionInfo.java
│       │   │   ├── OrbitSummary.java
│       │   │   ├── DiscoveryResult.java
│       │   │   └── TestMetadata.java
│       │   ├── device/
│       │   │   ├── DeviceProvisioner.java
│       │   │   ├── DefaultDeviceProvisioner.java
│       │   │   └── DeviceConnectionChecker.java
│       │   ├── discovery/
│       │   │   ├── TestDiscovery.java
│       │   │   └── internal/
│       │   │       └── LogRecordingTestRunListener.kt
│       │   ├── execution/
│       │   │   ├── TestExecutionContext.java
│       │   │   ├── TestExecutionPipeline.java
│       │   │   ├── RetryPolicy.java
│       │   │   └── actions/
│       │   │       ├── PreTestAction.java
│       │   │       ├── PostTestAction.java
│       │   │       ├── AppDataCleaner.java
│       │   │       ├── LogcatClearer.java
│       │   │       ├── VideoRecorder.java
│       │   │       ├── NetworkLogPuller.java
│       │   │       └── CoveragePuller.java
│       │   ├── collection/
│       │   │   ├── ArtifactCollector.java
│       │   │   ├── ScreenshotProcessor.java
│       │   │   ├── VideoProcessor.java
│       │   │   ├── FileProcessor.java
│       │   │   ├── NetworkLogProcessor.java
│       │   │   ├── LogcatMapper.java
│       │   │   └── CoverageCollector.java
│       │   ├── listener/
│       │   │   ├── RecentTestOutcomeProvider.java
│       │   │   ├── OrbitTestRunListener.java
│       │   │   └── MulticastTestRunListener.kt
│       │   ├── orchestration/
│       │   │   ├── DeviceSession.java
│       │   │   ├── DeviceOrchestrator.java
│       │   │   ├── DeviceWorker.java
│       │   │   └── MultiDeviceOrchestrator.java
│       │   ├── report/
│       │   │   ├── ReportRenderer.java
│       │   │   ├── OrbitReportRenderer.java
│       │   │   ├── JUnitXmlReportWriter.java
│       │   │   ├── ReportPublisher.java
│       │   │   ├── FileSystemPublisher.java
│       │   │   └── ReportArtifact.java
│       │   ├── selection/
│       │   │   ├── TestSelectionStrategy.java
│       │   │   └── ExternalIdSelectionStrategy.java
│       │   └── OrbitRunner.java
│       ├── test/kotlin/                 ← unit tests
│       └── integrationTest/kotlin/      ← integration tests (separate source set)
│
├── orbit-gradle-plugin/                 ← Gradle plugin `io.github.rajvinodh.orbit` (§16)
│   └── src/main/kotlin/io/github/rajvinodh/orbit/gradle/
│       ├── OrbitPlugin.kt               ← registers `orbit { }`, orbitRunner config, tasks
│       ├── OrbitExtension.kt            ← `orbit { }` DSL
│       ├── GradleOverrides.kt           ← -Porbit.* per-run overrides
│       ├── OrbitTestTask.kt             ← orbitTest / orbit<Variant>AndroidTest; runs the CLI main in a new JVM
│       └── AndroidVariantWiring.kt      ← AGP Variant API → per-variant tasks
│
├── orbit-client/                        ← Android library (OrbitRule equivalent)
│   └── src/main/java/com/phonepe/orbit/
│       └── OrbitRule.java
│
├── orbit-report/                        ← React frontend
│   ├── src/
│   │   ├── components/
│   │   │   ├── SummaryDashboard.tsx
│   │   │   ├── DeviceView.tsx
│   │   │   ├── TestRow.tsx
│   │   │   ├── TestDetail.tsx
│   │   │   ├── GanttTimeline.tsx
│   │   │   ├── CrossDeviceView.tsx
│   │   │   ├── ScreenshotCarousel.tsx
│   │   │   ├── VideoPlayer.tsx
│   │   │   └── LogcatViewer.tsx
│   │   ├── types/
│   │   │   └── report.ts                ← TypeScript types mirroring data.json
│   │   └── App.tsx
│   ├── package.json
│   └── vite.config.ts
│
└── test-app/                            ← sample app for E2E tests
```

---

## 3. Domain Model

All domain classes use **Lombok** (`@Value`, `@Builder`, `@Singular`). No manual boilerplate.

### TestCase

```java
@Value
public class TestCase {
    String className;
    String methodName;

    public String displayName() {
        return className + "#" + methodName;
    }

    public static TestCase from(TestIdentifier id) {
        return new TestCase(id.getClassName(), id.getTestName());
    }
}
```

### TestStatus

```java
public enum TestStatus {
    PASS, FAIL, IGNORED, ASSUMPTION_FAILURE
}
```

### TestResult

```java
@Value
@Builder
public class TestResult {
    TestCase testCase;
    TestStatus status;
    @Nullable StackTrace exception;
    long startedAt;           // epoch ms — required for Gantt timeline
    long durationMs;
    @Singular List<File> screenshots;
    @Singular List<File> videos;
    @Singular List<File> files;
    @Singular List<LogCatMessage> logcat;      // populated only on FAIL
    @Singular List<HtmlLog.LogEntry> crashLog; // AndroidRuntime tag only
    @Nullable String externalId;

    public boolean isCrashed() { return !crashLog.isEmpty(); }
    public boolean isPassed()  { return status == TestStatus.PASS; }
    public boolean isFailed()  { return status == TestStatus.FAIL; }
}
```

### DeviceStatus

```java
public enum DeviceStatus {
    /** APK install, permission grant, or directory clean failed. Device still reachable. */
    PROVISIONING_FAILED,

    /** ADB availability lost at any stage — provisioning or execution. */
    DISCONNECTED,

    /** Device ran tests to completion. Individual tests may still have failed. */
    COMPLETED
}
```

### DeviceDisconnectionInfo

```java
@Value
public class DeviceDisconnectionInfo {
    long discoveredAtMs;   // System.currentTimeMillis() when disconnect detected
}
```

### DeviceResult

```java
@Value
@Builder
public class DeviceResult {
    String serial;
    DeviceStatus status;
    @Nullable DeviceDetails deviceDetails;
    @Nullable String provisioningFailureMessage;
    long startedAt;
    long durationMs;
    @Singular Map<TestCase, TestResult> testResults;
    @Singular List<StackTrace> exceptions;
    @Singular List<LogCatMessage> deviceLogs;
    @Nullable DeviceDisconnectionInfo disconnection;

    public boolean isDisconnected()        { return status == DeviceStatus.DISCONNECTED; }
    public boolean isProvisioningFailed()  { return status == DeviceStatus.PROVISIONING_FAILED; }
}
```

### TestMetadata

```java
@Value
@Builder
public class TestMetadata {
    TestCase testCase;
    boolean clearAppDataBefore;
    boolean clearAppDataAfter;
    boolean clearLogcatBefore;
    @Nullable String externalId;   // from @ExternalId annotation — TMS-agnostic
}
```

### DiscoveryResult

```java
@Value
@Builder
public class DiscoveryResult {
    List<TestCase> activeTests;
    List<TestCase> ignoredTests;
    Map<TestCase, TestMetadata> metadata;
    Map<String, TestCase> byExternalId;   // externalId → TestCase, O(1) lookup
}
```

### OrbitSummary

```java
@Value
@Builder
public class OrbitSummary {
    String title;
    long startedAt;
    long durationMs;
    Map<String, DeviceResult> results;          // sanitised serial → DeviceResult
    List<TestCase> missedTests;                 // filtered by strategy — not run on any device
    String orbitVersion;
    ExecutionMode executionMode;                // DISTRIBUTE (default) or FANOUT
}
```

---

## 4. Configuration — RunConfig

Replaces the 40-parameter constructor. Single immutable value object built at CLI / programmatic entry.

```java
@Value
@Builder
public class RunConfig {

    // --- APKs ---
    File testApk;
    @Singular List<File> otherApks;

    // --- Devices ---
    @Singular Set<String> serials;
    @Singular Set<String> skipSerials;
    @Builder.Default boolean allowNoDevices = false;

    // --- Timing ---
    @Builder.Default Duration adbTimeout            = Duration.ofMinutes(10);
    @Builder.Default long    availabilityTimeoutMin = 30L;   // minutes; replaces deviceAvailabilityTimeout

    // --- Test scope ---
    @Nullable String className;
    @Nullable String methodName;
    @Builder.Default Map<String, String> instrumentationArgs = Collections.emptyMap();

    // --- Execution ---
    @Builder.Default boolean grantAll                    = false;
    @Builder.Default boolean clearAppDataBeforeEachTest  = false;
    @Builder.Default boolean codeCoverage                = false;

    // --- Observability ---
    @Builder.Default boolean trackLogcat      = false;
    @Builder.Default int     logcatLevel      = Log.LogLevel.INFO.getPriority();
    @Builder.Default boolean recordVideo      = false;   // gates VideoProcessor + VideoRecorder
    @Builder.Default boolean trackNetworkLogs = false;   // gates NetworkLogProcessor + NetworkLogPuller

    // --- Appium Settings provisioning ---
    // null = skip install; non-null = ensure this version is on device before tests run
    @Builder.Default @Nullable String appiumSettingsVersion = "7.0.24";

    // --- Execution mode ---
    // DISTRIBUTE (default): each test runs on one device (shared queue)
    // FANOUT: all N tests run on ALL D devices; summary shows aggregated worst-wins result
    @Builder.Default ExecutionMode executionMode = ExecutionMode.DISTRIBUTE;

    // --- Test selection ---
    @Builder.Default TestSelectionStrategy selectionStrategy = TestSelectionStrategy.ALL;

    // --- Output ---
    @Builder.Default File output = new File("orbit-output");
    @Nullable File androidSdk;
    @Nullable String reportTitle;   // null = auto: "{package} · {date}"

    // --- Report publishing ---
    @Builder.Default List<ReportPublisher> reportPublishers = List.of(new FileSystemPublisher());

    // --- External listeners ---
    @Singular List<ITestRunListener> testRunListeners;

    // --- Retry ---
    @Builder.Default RetryPolicy retryPolicy = RetryPolicy.NONE;

    // --- Debug ---
    @Builder.Default boolean debug = false;
}
```

---

## 5. Extension Points

The system is designed so that **adding a feature = adding a class**. No existing class should need modification.

| Interface | Method | Purpose | Register via |
|---|---|---|---|
| `PreTestAction` | `void before(IDevice, TestExecutionContext)` | Runs before each test | `RunConfig` (built into pipeline) |
| `PostTestAction` | `void after(IDevice, TestExecutionContext, boolean failed)` | Runs after each test | `RunConfig` (built into pipeline) |
| `ArtifactCollector` | `void collect(IDevice, DeviceSession)` | Pulls / processes artifacts after device done | `RunConfig` |
| `RetryPolicy` | `boolean shouldRetry(TestExecutionContext, TestResult)` | Whether to re-run a test | `RunConfig.retryPolicy` |
| `TestSelectionStrategy` | `List<TestCase> select(DiscoveryResult)` | Which tests to run | `RunConfig.selectionStrategy` |
| `ReportPublisher` | `void publish(ReportArtifact)` | Where to send the report | `RunConfig.reportPublishers` |
| `ITestRunListener` | (DDMLib interface) | External result listeners | `RunConfig.testRunListeners` |
| `DeviceProvisioner` | `ProvisionResult provision(IDevice, RunConfig)` | How to set up a device | Constructor injection into `DeviceOrchestrator` |

### RetryPolicy

```java
@FunctionalInterface
public interface RetryPolicy {
    RetryPolicy NONE = (ctx, result) -> false;

    boolean shouldRetry(TestExecutionContext ctx, TestResult result);
}

// Future example — add without touching any existing class:
public class MaxAttemptsRetryPolicy implements RetryPolicy {
    private final int maxAttempts;

    @Override
    public boolean shouldRetry(TestExecutionContext ctx, TestResult result) {
        return result.isFailed() && ctx.getAttemptNumber() < maxAttempts;
    }
}
```

### TestSelectionStrategy

```java
@FunctionalInterface
public interface TestSelectionStrategy {
    TestSelectionStrategy ALL = discovery -> new ArrayList<>(discovery.getActiveTests());

    List<TestCase> select(DiscoveryResult discovery);
}
```

### ExternalIdSelectionStrategy

```java
public class ExternalIdSelectionStrategy implements TestSelectionStrategy {
    private final List<String> externalIds;

    /** Accepts comma-separated IDs or @/path/to/file (one ID per line). */
    public static ExternalIdSelectionStrategy from(String input) { ... }

    @Override
    public List<TestCase> select(DiscoveryResult discovery) {
        return externalIds.stream()
            .map(id -> discovery.getByExternalId().get(id))
            .filter(Objects::nonNull)
            .collect(toList());
    }
}
```

---

## 6. Discovery

Runs a single `log=true` dry-run instrumentation call on one device. Internal implementation detail — callers receive a typed `DiscoveryResult`, never raw HashMaps.

```java
public class TestDiscovery {

    public DiscoveryResult run(IDevice device) throws Exception {
        LogRecordingTestRunListener recorder = new LogRecordingTestRunListener();
        RemoteAndroidTestRunner runner = createLogRunner(device);
        runner.addBooleanArg("log", true);
        runner.run(recorder);

        if (Boolean.TRUE.equals(recorder.isTestRunFailed())) {
            throw new RuntimeException("Dry-run failed: " + recorder.getRunName());
        }

        return buildDiscoveryResult(recorder);
    }

    private DiscoveryResult buildDiscoveryResult(LogRecordingTestRunListener recorder) {
        List<TestCase> active  = recorder.activeTests().stream().map(TestCase::from).collect(toList());
        List<TestCase> ignored = recorder.ignoredTests().stream().map(TestCase::from).collect(toList());

        Map<TestCase, TestMetadata> metadata = new LinkedHashMap<>();
        Map<String, TestCase> byExternalId   = new LinkedHashMap<>();

        for (TestCase test : active) {
            TestMetadata meta = buildMetadata(test, recorder);
            metadata.put(test, meta);
            if (meta.getExternalId() != null) {
                byExternalId.put(meta.getExternalId(), test);
            }
        }

        return DiscoveryResult.builder()
            .activeTests(active)
            .ignoredTests(ignored)
            .metadata(metadata)
            .byExternalId(byExternalId)
            .build();
    }

    private TestMetadata buildMetadata(TestCase test, LogRecordingTestRunListener recorder) {
        List<String> annotations = recorder.annotationsFor(test);
        String clearAction       = recorder.clearAppDataActionFor(test);  // BEFORE / AFTER / BOTH / null
        String externalId        = recorder.externalIdFor(test);

        return TestMetadata.builder()
            .testCase(test)
            .clearAppDataBefore(clearAction != null && clearAction.contains("BEFORE"))
            .clearAppDataAfter(clearAction != null && clearAction.contains("AFTER"))
            .clearLogcatBefore(annotations.contains("ClearLogCat"))
            .externalId(externalId)
            .build();
    }
}
```

**`LogRecordingTestRunListener`** (internal to `discovery/` package):
- Strips parameterised class name suffixes `[...]`
- Parses `setCustomTestDetails` from instrumentation: annotations, externalId, clearAppDataAction
- Kept internal — never exposed beyond `TestDiscovery`

---

## 7. Device Provisioning

Each step is a focused private method. Disconnection detection is extracted into one place.

```java
public interface DeviceProvisioner {
    ProvisionResult provision(IDevice device, RunConfig config);
}

public enum ProvisionResult {
    SUCCESS, PROVISIONING_FAILED, DISCONNECTED
}
```

```java
public class DefaultDeviceProvisioner implements DeviceProvisioner {

    private final AndroidDebugBridge adb;
    private final DeviceConnectionChecker connectionChecker;

    @Override
    public ProvisionResult provision(IDevice device, RunConfig config) {
        if (!installApks(device, config))              return resolveFailure(device);
        if (!installAppiumSettings(device, config))    return resolveFailure(device);
        if (!cleanDeviceDirectories(device))           return resolveFailure(device);
        if (!grantPermissions(device, config))         return resolveFailure(device);
        if (!clearLogcat(device))                      return resolveFailure(device);
        return ProvisionResult.SUCCESS;
    }

    /** Returns DISCONNECTED if device is gone, otherwise PROVISIONING_FAILED. */
    private ProvisionResult resolveFailure(IDevice device) {
        return connectionChecker.isAvailable(adb, device.getSerialNumber())
            ? ProvisionResult.PROVISIONING_FAILED
            : ProvisionResult.DISCONNECTED;
    }

    private boolean installApks(IDevice device, RunConfig config) { ... }

    /**
     * Skipped when config.getAppiumSettingsVersion() == null (--no-appium-settings).
     * Checks installed version via `dumpsys package`. Downloads from GitHub releases if
     * absent or outdated. Decoupled from video recording.
     */
    private boolean installAppiumSettings(IDevice device, RunConfig config) { ... }

    private boolean cleanDeviceDirectories(IDevice device) { ... }
    private boolean grantPermissions(IDevice device, RunConfig config) { ... }
    private boolean clearLogcat(IDevice device) { ... }
}
```

```java
public class DeviceConnectionChecker {
    public boolean isAvailable(AndroidDebugBridge adb, String serial) {
        // single place for the isDeviceAvailable check (was in OrbitUtils, scattered everywhere)
    }
}
```

---

## 8. Execution Pipeline

### TestExecutionContext

Passed to every `PreTestAction` and `PostTestAction`. Extensible — add fields without changing interfaces.

```java
@Value
@Builder
public class TestExecutionContext {
    TestCase testCase;
    TestMetadata metadata;
    String deviceSerial;
    String externalStorage;
    @Builder.Default int attemptNumber = 0;   // incremented by RetryPolicy loop
}
```

### Actions

```java
@FunctionalInterface
public interface PreTestAction {
    void before(IDevice device, TestExecutionContext ctx) throws Exception;
}

@FunctionalInterface
public interface PostTestAction {
    void after(IDevice device, TestExecutionContext ctx, boolean testFailed) throws Exception;
}
```

| Action | Pre | Post | Behaviour |
|---|---|---|---|
| `AppDataCleaner` | ✓ | ✓ | `pm clear` when `clearBefore` / `clearAfter` flags set in metadata |
| `LogcatClearer` | ✓ | | `logcat -c` when `clearLogcatBefore` flag set |
| `VideoRecorder` | ✓ | ✓ | Start recording pre-test; stop+poll post-test; delete video if test passed |
| `NetworkLogPuller` | | ✓ | Pull Chucker DB only when `testFailed=true` |

### TestExecutionPipeline

```java
public class TestExecutionPipeline {

    private final List<PreTestAction> preActions;
    private final List<PostTestAction> postActions;
    private final RetryPolicy retryPolicy;

    public void execute(IDevice device, TestExecutionContext ctx,
                        RemoteAndroidTestRunner runner,
                        Collection<ITestRunListener> listeners,
                        RecentTestOutcomeProvider outcomeProvider) throws Exception {
        int attempt = 0;
        TestResult result;
        do {
            ctx = ctx.withAttemptNumber(attempt);

            for (PreTestAction action : preActions) {
                action.before(device, ctx);
            }

            runner.run(listeners);

            boolean failed = outcomeProvider.didRecentTestFail();
            for (PostTestAction action : postActions) {
                action.after(device, ctx, failed);
            }

            result = outcomeProvider.getLastResult();
            attempt++;
        } while (retryPolicy.shouldRetry(ctx, result));
    }
}
```

### VideoRecorder detail

```java
public class VideoRecorder implements PreTestAction, PostTestAction {

    @Override
    public void before(IDevice device, TestExecutionContext ctx) {
        String filename = buildFilename(ctx.getTestCase());
        shellExec(device, String.format(START_RECORDING_CMD, filename));
    }

    @Override
    public void after(IDevice device, TestExecutionContext ctx, boolean testFailed) {
        stopAndWaitForCompletion(device, ctx);
        if (!testFailed) {
            deleteVideo(device, ctx);   // don't keep video for passing tests
        }
    }

    private void stopAndWaitForCompletion(IDevice device, TestExecutionContext ctx) {
        for (int i = 1; i <= MAX_POLL_ATTEMPTS; i++) {
            shellExec(device, STOP_RECORDING_CMD);
            if (isFileCompleted(device, ctx)) return;
            sleep(POLL_INTERVAL_MS);
        }
        log.error("Video did not stop in time for {}", ctx.getTestCase().displayName());
    }
}
```

---

## 9. Multi-Device Orchestration

### Phases

```
Phase 0 — Clear output dir (config.getOutput() is deleted wholesale before anything runs —
                             mirrors Spoon's SpoonRunner.runTests(); safe because output is
                             write-only, never read as input)
Phase 1 — Discovery        (sequential, one device)
Phase 2 — Provision all    (parallel, CountDownLatch)
Phase 3 — Execute          (parallel; shared queue in DISTRIBUTE, per-device queue in FANOUT)
Phase 4 — Collect          (parallel, CountDownLatch)
Phase 5 — Report
```

### DeviceSession

Per-device mutable state during a run. Lives from Phase 2 → Phase 4.

```java
public class DeviceSession {
    private final DeviceResult.Builder resultBuilder;
    private final MulticastTestRunListener multicast;
    private final @Nullable OrbitDeviceLogger logcatLogger;   // null if trackLogcat=false

    public void markDisconnected() {
        resultBuilder.status(DeviceStatus.DISCONNECTED)
                     .disconnection(new DeviceDisconnectionInfo(System.currentTimeMillis()));
    }
}
```

### DeviceWorker

```java
public class DeviceWorker implements Runnable {

    private final IDevice device;
    private final BlockingQueue<TestCase> queue;
    private final DeviceOrchestrator orchestrator;
    private final long availabilityTimeoutMin;
    private final DiscoveryResult discovery;

    @Override
    public void run() {
        DeviceSession session = orchestrator.startSession(device);
        try {
            TestCase test;
            while ((test = queue.poll(availabilityTimeoutMin, TimeUnit.MINUTES)) != null) {
                try {
                    orchestrator.execute(device, test, discovery.getMetadata().get(test), session);
                } catch (DeviceDisconnectedException e) {
                    session.markDisconnected();
                    return;   // worker exits — remaining tests stay in queue for other workers
                } catch (Exception e) {
                    session.getResultBuilder().exception(e);
                    // continue to next test (non-disconnect exceptions don't stop the worker)
                }
            }
            // queue.poll() returned null → timeout or queue empty → worker done normally
        } finally {
            orchestrator.endSession(device, session);
        }
    }
}
```

### MultiDeviceOrchestrator

```java
public class MultiDeviceOrchestrator {

    public OrbitSummary run(Set<String> serials, RunConfig config,
                             AndroidDebugBridge adb) {
        OrbitSummary.Builder summary = OrbitSummary.builder();

        // Phase 1: Discovery on one random device
        DiscoveryResult discovery = runDiscovery(serials, config, adb, summary);
        List<TestCase> testsToRun = config.getSelectionStrategy().select(discovery);

        // Pre-populate ignored tests in summary
        recordIgnoredTests(discovery, testsToRun, summary);

        // Phase 2: Provision all devices in parallel
        BlockingQueue<TestCase> queue = new LinkedBlockingQueue<>(testsToRun);
        Map<String, DeviceOrchestrator> orchestrators = provisionAll(serials, config, adb, summary);

        // Phase 3: Execute — one DeviceWorker per provisioned device
        CountDownLatch executionDone = new CountDownLatch(orchestrators.size());
        for (Map.Entry<String, DeviceOrchestrator> entry : orchestrators.entrySet()) {
            executor.submit(new DeviceWorker(
                entry.getValue().getDevice(), queue, entry.getValue(),
                config.getAvailabilityTimeoutMin(), discovery,
                executionDone));
        }
        awaitUninterruptibly(executionDone);

        // Phase 4: Collect artifacts in parallel
        CountDownLatch collectDone = new CountDownLatch(orchestrators.size());
        for (DeviceOrchestrator orchestrator : orchestrators.values()) {
            executor.submit(() -> {
                try {
                    DeviceResult result = orchestrator.collectAndBuild(adb);
                    synchronized (summary) { summary.result(result); }
                } finally {
                    collectDone.countDown();
                }
            });
        }
        awaitUninterruptibly(collectDone);

        return summary.build();
    }

    /**
     * Device disconnection during Phase 1: try next device.
     * All devices fail: throw RuntimeException.
     */
    private DiscoveryResult runDiscovery(...) { ... }
}
```

### DeviceDisconnection — all scenarios

| Scenario | Outcome |
|---|---|
| Disconnect during Phase 1 (discovery) | Try next serial. All fail → abort entire run. |
| Disconnect during Phase 2 (provisioning) | `DeviceResult.status = DISCONNECTED`. Not added to worker pool. Other devices continue. |
| Disconnect mid-test (Phase 3) DISTRIBUTE | Worker catches `DeviceDisconnectedException`. Session marked `DISCONNECTED`. Remaining tests stay in shared queue for other workers. Tests left after all workers exit are injected into first device result as `IGNORED` with reason. |
| Disconnect mid-test (Phase 3) FANOUT | Worker drains its own per-device queue: in-flight test + all remaining tests added to session as `IGNORED` with reason `"Device disconnected during test run"`. |
| Disconnect during Phase 4 (collect) | `DeviceResult` built with partial artifacts. Exception logged. `status` remains `DISCONNECTED`. |

### ExecutionMode — DISTRIBUTE vs FANOUT

| | DISTRIBUTE (default) | FANOUT |
|---|---|---|
| Queue | One shared `LinkedBlockingQueue` | Per-device `LinkedBlockingQueue(testsToRun)` |
| Each test runs | On one device (whoever polls first) | On every provisioned device |
| globalTotal | N | N × D |
| On disconnect | Remaining tests stay in shared queue | Worker drains own queue → IGNORED with reason |
| Queue-drained after all workers exit | Injected into first device as IGNORED | N/A — per-device drain handles it |
| Report summary total | N unique tests | N unique tests (aggregated) |
| Report `tests` array | `null` | Present — one entry per unique test with `aggregatedStatus` + `deviceStatuses` |
| Status aggregation | Per-device | Worst-wins: `FAIL > ASSUMPTION_FAILURE > IGNORED > PASS` |

#### Progress logging

Both modes log each completion on two counters:
```
[7/30] [emulator-5554: 3/10] PASS com.Foo#testBar (1234ms)
 ^  ^           ^          ^
 global        per-device
```

---

## 10. Artifact Collection

Each collector is independent. `DeviceOrchestrator.collectAndBuild()` runs them all in sequence.

```java
public interface ArtifactCollector {
    void collect(IDevice device, DeviceSession session) throws IOException;
}
```

| Collector | When added | Responsibility |
|---|---|---|
| `ScreenshotProcessor` | always | Pull `app_orbit-screenshots/` from device → `images/{serial}/`. Associate with `TestResult`. |
| `VideoProcessor` | `recordVideo=true` | Move scrcpy MP4 files from `videos/{serial}/` to `TestResult`. Passed-test videos already deleted on-device by `VideoRecorder`. |
| `FileProcessor` | always | Pull `app_orbit-files/` from device → `files/{serial}/`. Associate with `TestResult`. |
| `NetworkLogProcessor` | `trackNetworkLogs=true` | Map Chucker DB files in `network/{serial}/` to tests by filename pattern `{class}-{method}-{ts}-chucker.db`. Output dir is `network/{serial}/` — no extra nesting. |
| `LogcatMapper` | `trackLogcat=true` | Stop `OrbitDeviceLogger`. Map log messages to `TestResult` for failed tests only. Attach device-level logs to `DeviceResult`. |
| `CoverageCollector` | `codeCoverage=true` | Pull per-test `coverage.ec` files. Merge via `CoverageMerger` after all tests. |

---

## 11. Listeners

### RecentTestOutcomeProvider

Eliminates the `instanceof OrbitTestRunListener` check in `OrbitAndroidTestRunner`.

```java
public interface RecentTestOutcomeProvider {
    boolean didRecentTestFail();
}
```

### OrbitTestRunListener

```java
final class OrbitTestRunListener implements ITestRunListener, RecentTestOutcomeProvider {

    private final DeviceResult.Builder deviceResultBuilder;
    private final Map<TestIdentifier, TestResult.Builder> inProgress = new HashMap<>();
    private boolean recentTestFailed = false;

    @Override public void testStarted(TestIdentifier test) {
        inProgress.put(test, TestResult.builder()
            .testCase(TestCase.from(test))
            .startedAt(System.currentTimeMillis())
            .status(TestStatus.PASS));
        recentTestFailed = false;
    }

    @Override public void testFailed(TestIdentifier test, String trace) {
        inProgress.get(test).status(TestStatus.FAIL).exception(StackTrace.from(trace));
        recentTestFailed = true;
    }

    @Override public void testAssumptionFailure(TestIdentifier test, String trace) {
        inProgress.get(test).status(TestStatus.ASSUMPTION_FAILURE).exception(StackTrace.from(trace));
        recentTestFailed = true;
    }

    @Override public void testIgnored(TestIdentifier test) {
        inProgress.computeIfAbsent(test, t -> TestResult.builder()
            .testCase(TestCase.from(t)).startedAt(System.currentTimeMillis()))
            .status(TestStatus.IGNORED);
    }

    @Override public void testEnded(TestIdentifier test, Map<String, String> metrics) {
        TestResult.Builder builder = inProgress.remove(test);
        builder.durationMs(System.currentTimeMillis() - builder.getStartedAt());
        deviceResultBuilder.testResult(TestCase.from(test), builder.build());
    }

    @Override public void testRunFailed(String errorMessage) {
        deviceResultBuilder.exception(StackTrace.from(errorMessage));
    }

    @Override public boolean didRecentTestFail() { return recentTestFailed; }
}
```

### MulticastTestRunListener

```kotlin
internal class MulticastTestRunListener(
    private val delegates: List<ITestRunListener>
) : ITestRunListener {

    private val stopwatch = Stopwatch.createUnstarted()
    private var started = false
    private var stopped = false

    fun multiRunStarted(runName: String, testCount: Int) {
        check(!started) { "Already started" }
        started = true
        stopwatch.start()
        delegates.forEach { it.testRunStarted(runName, testCount) }
    }

    fun multiRunEnded() {
        check(started && !stopped) { "Invalid state" }
        stopped = true
        val elapsed = stopwatch.elapsed(TimeUnit.SECONDS)
        delegates.forEach { it.testRunEnded(elapsed, emptyMap()) }
    }

    override fun testStarted(test: TestIdentifier)                             = delegates.forEach { it.testStarted(test) }
    override fun testFailed(test: TestIdentifier, trace: String)               = delegates.forEach { it.testFailed(test, trace) }
    override fun testAssumptionFailure(test: TestIdentifier, trace: String)    = delegates.forEach { it.testAssumptionFailure(test, trace) }
    override fun testIgnored(test: TestIdentifier)                             = delegates.forEach { it.testIgnored(test) }
    override fun testEnded(test: TestIdentifier, metrics: Map<String, String>) = delegates.forEach { it.testEnded(test, metrics) }
    override fun testRunFailed(errorMessage: String)                           = delegates.forEach { it.testRunFailed(errorMessage) }  // was TODO — fixed

    // Suppressed per-test-run — session level handles these via multiRunStarted/Ended
    override fun testRunStarted(runName: String, testCount: Int) {}
    override fun testRunStopped(elapsedTime: Long) {}
    override fun testRunEnded(elapsedTime: Long, runMetrics: Map<String, String>) {}
}
```

---

## 12. Report

### Stack

| Concern | Choice |
|---|---|
| Frontend framework | React + TypeScript |
| Build tool | Vite |
| Styling | TailwindCSS |
| Charts | Recharts |
| Syntax highlighting | Prism.js |
| Bundle embedded in | orbit-runner JAR resources |

### Output Structure

```
orbit-report/
  index.html          ← SPA shell (data embedded as window.__ORBIT_DATA__ inline JSON)
  data.json           ← all test data (generated by OrbitReportRenderer)
  static/
    main.js           ← compiled React bundle (IIFE; from JAR resources)
    main.css          ← compiled Tailwind
  images/
    {serial}/{class}/{method}/{timestamp}.png
  videos/
    {serial}/{class}/{method}/{name}.mp4
  files/
    {serial}/{class}/{method}/{filename}
  network/
    {serial}/{class}-{method}-{ts}-chucker.db
  junit/
    TEST-{serial}.xml ← JUnit XML per device (generated by JUnitXmlReportWriter)
```

### JUnitXmlReportWriter

Writes one `TEST-{serial}.xml` per device to `orbit-report/junit/`. Compatible with GitHub Actions `junit-reporter`, Jenkins, and CircleCI.

| TestStatus | XML element |
|---|---|
| `PASS` | self-closing `<testcase/>` |
| `FAIL` | `<testcase><failure message type>…stack trace…</failure></testcase>` |
| `IGNORED` | `<testcase><skipped/></testcase>` |
| `ASSUMPTION_FAILURE` | `<testcase><skipped message="Assumption failed"/></testcase>` |

User copies `orbit-report/` to wherever they want (S3, CI artifacts, local). Report opens via `index.html`.

### Report Views

**Summary Dashboard**
- Total / Passed / Failed / Ignored cards
- Pass rate donut chart (Recharts)
- Gantt timeline — per device, per test, coloured by status (requires `startedAt` per `TestResult`)
- Failed tests list — click to jump to device+test

**Per-Device View**
- Device info header (model, OS version, API level, duration, status)
- Filter bar: All / Failed / Passed / Ignored
- Test table — expandable rows

**Expanded Test Row**
- Screenshot carousel with navigation
- Inline video player (HTML5 `<video>`)
- Stack trace (Prism.js syntax highlighted)
- Logcat viewer — filterable by tag, coloured by level

**Cross-Device Comparison View**
- Triggered when same test ran on multiple devices
- Side-by-side status per device
- Frontend-computed on load from device-centric `data.json` (no duplication in JSON)

### data.json

```json
{
  "meta": {
    "title": "com.example.app · 2026-04-24 14:32",
    "startedAt": 1745488800000,
    "durationMs": 272000,
    "orbitVersion": "1.0.0"
  },
  "summary": {
    "total": 120,
    "passed": 108,
    "failed": 8,
    "ignored": 4
  },
  "devices": [
    {
      "serial": "emulator-5554",
      "status": "COMPLETED",
      "model": "Pixel 7",
      "apiLevel": 33,
      "osVersion": "13",
      "durationMs": 252000,
      "disconnectedAt": null,
      "tests": [
        {
          "className": "com.example.LoginTest",
          "methodName": "testInvalidPassword",
          "status": "FAIL",
          "startedAt": 1745488810000,
          "durationMs": 2400,
          "externalId": "C1234",
          "exception": "java.lang.AssertionError: expected true but was false\n  at ...",
          "screenshots": ["images/emulator-5554/LoginTest/testInvalidPassword/1.png"],
          "video": "videos/emulator-5554/LoginTest/testInvalidPassword/rec.mp4",
          "logcat": [
            { "time": "14:32:01.123", "level": "E", "tag": "MyApp", "pid": 1234, "msg": "..." }
          ],
          "crashLog": [
            { "time": "14:32:01.200", "level": "E", "tag": "AndroidRuntime", "msg": "FATAL..." }
          ]
        }
      ]
    }
  ]
}
```

### ReportPublisher extension point

```java
public interface ReportPublisher {
    void publish(ReportArtifact artifact);
}

@Value
public class ReportArtifact {
    File rootDir;     // orbit-report/
    File indexFile;   // orbit-report/index.html
}

// Default — files already on disk, nothing to do
public class FileSystemPublisher implements ReportPublisher {
    @Override public void publish(ReportArtifact artifact) { /* no-op */ }
}

// Future: S3Publisher, SlackPublisher — add without touching OrbitRunner
```

---

## 13. CLI

**Library:** [Clikt](https://github.com/ajalt/clikt) (replaces `kotlin-argparser`)

**Rationale:** Idiomatic Kotlin, better help generation, built-in file/type validation, `@file` expansion for `--external-ids`, subcommand support for future `orbit devices` / `orbit report` commands.

```kotlin
class OrbitCommand : CliktCommand(
    name = "orbit",
    help = "Run Android instrumentation tests across multiple devices"
) {
    // --- Required ---
    val testApk   by argument("TEST_APK").file(mustExist = true)
    val otherApks by argument("OTHER_APKS").file(mustExist = true).multiple()

    // --- App identity (all optional — read from the test APK's manifest by ApkManifestReader) ---
    val appPackage  by option("--app-package", "-p")   // <instrumentation android:targetPackage>
    val testPackage by option("--test-package")        // <manifest package>
    val testRunner  by option("--test-runner")         // <instrumentation android:name>

    // --- Devices ---
    val serials     by option("--serial",      help = "Device serial to target (repeatable)").multiple()
    val skipSerials by option("--skip-serial", help = "Device serial to skip (repeatable)").multiple()
    val allowNoDevices by option("--allow-no-devices").flag()

    // --- Timing ---
    val adbTimeout by option("--adb-timeout",
        help = "ADB command timeout, ISO-8601 e.g. PT10M")
        .convert { Duration.parse(it) }
        .default(Duration.ofMinutes(10))
    val availabilityTimeout by option("--availability-timeout",
        help = "Max minutes to wait for a free device (default: 30)")
        .long().default(30L)

    // --- Test scope ---
    val className  by option("--class-name",  help = "Fully-qualified class to run")
    val methodName by option("--method-name", help = "Method to run (requires --class-name)")
    val instrumentationArgs by option("-e", help = "Instrumentation arg (repeatable): -e KEY VALUE")
        .pair().multiple()

    // --- Execution ---
    val executionMode by option("--execution-mode",
        help = "DISTRIBUTE (default) or FANOUT (all tests on all devices)")
        .convert { ExecutionMode.valueOf(it.uppercase()) }
        .default(ExecutionMode.DISTRIBUTE)
    val grantAll     by option("--grant-all").flag()
    val clearAppData by option("--clear-app-data",
        help = "Run 'pm clear' before each test").flag()
    val coverage     by option("--coverage").flag()

    // --- Observability ---
    val trackLogcat     by option("--logcat",
        help = "Capture logcat; mapped to failed tests only").flag()
    val logcatLevel     by option("--logcat-level",
        help = "Min logcat level: V/D/I/W/E (default: I)").default("I")
    val recordVideo     by option("--video").flag()
    val trackNetworkLogs by option("--network-logs",
        help = "Capture Chucker DB per failed test").flag()

    // --- Test selection ---
    val externalIds by option("--external-ids",
        help = "Comma-separated external IDs to run, or @/path/to/ids.txt")

    // --- Output ---
    val output by option("--output", "-o",
        help = "Output directory (default: orbit-output)")
        .file().default(File("orbit-output"))
    val sdk    by option("--sdk",
        help = "Android SDK path. Defaults to \$ANDROID_HOME").file()
    val title  by option("--title",
        help = "Report title. Default: '<package> · <date>'")

    // --- Appium Settings ---
    val appiumSettingsVersion by option("--appium-settings-version",
        help = "io.appium.settings version to ensure on device (default: 7.0.24)")
        .default("7.0.24")
    val noAppiumSettings by option("--no-appium-settings",
        help = "Skip io.appium.settings installation entirely").flag()

    // --- Misc ---
    val debug      by option("--debug").flag()
    val alwaysZero by option("--always-zero",
        help = "Always exit 0 regardless of failures").flag()

    override fun run() {
        val selectionStrategy = externalIds
            ?.let { ExternalIdSelectionStrategy.from(it) }
            ?: TestSelectionStrategy.ALL

        val config = RunConfig.builder()
            .testApk(testApk)
            .otherApks(otherApks)
            .serials(serials.toSet())
            .skipSerials(skipSerials.toSet())
            .allowNoDevices(allowNoDevices)
            .adbTimeout(adbTimeout)
            .availabilityTimeoutMin(availabilityTimeout)
            .className(className)
            .methodName(methodName)
            .instrumentationArgs(instrumentationArgs.toMap())
            .grantAll(grantAll)
            .clearAppDataBeforeEachTest(clearAppData)
            .codeCoverage(coverage)
            .trackLogcat(trackLogcat)
            .logcatLevel(RunConfig.logcatPriority(logcatLevel))
            .recordVideo(recordVideo)
            .trackNetworkLogs(trackNetworkLogs)
            .appiumSettingsVersion(if (noAppiumSettings) null else appiumSettingsVersion)
            .selectionStrategy(selectionStrategy)
            .output(output)
            .androidSdk(sdk ?: resolveAndroidHome())
            .reportTitle(title)
            .debug(debug)
            .build()

        val success = OrbitRunner(config).run()
        if (!success && !alwaysZero) exitProcess(1)
    }
}

fun main(args: Array<String>) = OrbitCommand().main(args)
```

**Exit codes:**
- `0` — all tests passed (or `--always-zero`)
- `1` — one or more tests failed
- `2` — configuration / argument error (Clikt handles automatically)

**APK manifest inference (`config/ApkManifestReader`):** `OrbitRunner.run()` first calls
`ApkManifestReader.fillMissing(config)`, which reads the test APK's binary manifest (`net.dongliu:apk-parser`):
- `appPackage` ← `<instrumentation android:targetPackage>` (only if not supplied)
- `testPackage` ← `<manifest package>` (only if not supplied)
- `testRunner` ← `<instrumentation android:name>` (only while still the default `AndroidJUnitRunner`)

Values that were set explicitly are never changed. If parsing fails, the config is left as it is. If `appPackage` is still
unknown afterwards, the run aborts (returns `false`) before ADB is started. So the minimal invocation is just
`orbit test.apk app.apk`.

---

## 14. Testing Strategy

### Libraries

| Purpose | Library |
|---|---|
| Test framework | JUnit 5 |
| Mocking | MockK (Kotlin-native) |
| Assertions | AssertJ |
| File operations | JUnit 5 `@TempDir` |
| JSON snapshot | Custom `assertJson` helper comparing to `__snapshots__/` files |

### Source sets

```
orbit-runner/src/
  test/kotlin/               ← unit tests (always run)
  integrationTest/kotlin/    ← integration tests (separate Gradle source set, CI-gated)
```

---

### Unit Tests

#### `DefaultDeviceProvisionerTest`
```
✓ installApks success → returns SUCCESS
✓ installApks InstallException, device reachable → PROVISIONING_FAILED
✓ installApks InstallException, device gone → DISCONNECTED
✓ step order: install → installAppiumSettings → clean → grant → clear logcat
✓ appiumSettingsVersion=null → installAppiumSettings skipped entirely
✓ correct version already installed → no download, no reinstall
✓ outdated version installed → uninstall + download + reinstall
✓ compareVersions: a > b, a == b, a < b, different length segments
✓ grantAll=false → no -g flag on install
✓ grantAll=true, API < 23 → no -g flag
✓ grantAll=true, API >= 23 → -g flag present
✓ API >= 23 → READ/WRITE_EXTERNAL_STORAGE granted
✓ API >= 30 → MANAGE_EXTERNAL_STORAGE granted
✓ cleanDirectories → rm -rf sent for each dir
```

#### `TestExecutionPipelineTest`
```
✓ preActions called in declared order before run()
✓ postActions called with failed=false after passing test
✓ postActions called with failed=true after failing test
✓ DeviceDisconnectedException propagates (not swallowed)
✓ RetryPolicy.NONE → single execution
✓ RetryPolicy max 2 → re-executes on fail, stops on pass
✓ attemptNumber incremented across retries
```

#### `DeviceWorkerTest`
```
✓ drains full queue when device healthy
✓ DeviceDisconnectedException → session marked DISCONNECTED, worker exits
✓ remaining tests stay in queue after disconnect (other workers pick them up)
✓ queue.poll() timeout → worker exits cleanly
✓ non-disconnect exception → logged, worker continues to next test
```

#### `OrbitTestRunListenerTest`
```
✓ testStarted → builder created, recentTestFailed=false
✓ testFailed → status=FAIL, recentTestFailed=true
✓ testAssumptionFailure → status=ASSUMPTION_FAILURE, recentTestFailed=true
✓ testIgnored → status=IGNORED
✓ testEnded → result added to DeviceResult.Builder with duration
✓ testRunFailed → exception added to DeviceResult.Builder
✓ didRecentTestFail() correct after pass then fail sequence
✓ didRecentTestFail() resets to false on next testStarted
```

#### `MulticastTestRunListenerTest`
```
✓ all delegates receive testStarted, testFailed, testIgnored, testEnded
✓ testRunFailed properly delegated to ALL delegates (was TODO bug — now fixed)
✓ multiRunStarted → delegates get testRunStarted
✓ multiRunEnded → delegates get testRunEnded with elapsed time
✓ per-test testRunStarted/Ended suppressed
✓ multiRunStarted twice → IllegalStateException
```

#### `LogcatMapperTest` (pure logic, no device)
```
✓ MESSAGE_START pattern matches "started: method(class)"
✓ MESSAGE_END pattern matches "finished: ..."
✓ logs within same PID attributed to active test
✓ logs outside PID filtered
✓ logs below logcatLevel filtered
✓ appPackage logs always included regardless of PID
✓ device-level logs captured when no test active
✓ no MESSAGE_END before next START → previous test closed, new opens
✓ FAIL test → logcat attached
✓ PASS test → logcat NOT attached
```

#### `ScreenshotProcessorTest`
```
using @TempDir:
✓ screenshots moved to imageDir/{serial}/{class}/{method}/
✓ screenshot associated with correct TestResult
✓ multiple screenshots → all associated, no GIF produced
✓ unknown test directory → error logged, no exception thrown
```

#### `VideoProcessorTest`
```
✓ {class}-{method}-{timestamp}.mp4 parsed to correct TestCase
✓ failed test → video added to TestResult
✓ passed test → no video in TestResult (deleted on-device by VideoRecorder)
✓ malformed filename → error logged, skipped
```

#### `VideoRecorderTest`
```
✓ before() → start recording shell command sent with correct filename
✓ after(failed=true) → stop sent, file completion polled, no delete
✓ after(failed=false) → stop sent, delete command sent
✓ isFileCompleted: size stable across two checks → true
✓ isFileCompleted: file not found → true (already cleaned)
✓ poll loop exits after MAX_POLL_ATTEMPTS
```

#### `AppDataCleanerTest`
```
✓ clearBefore=true → pm clear sent in before()
✓ clearBefore=false → pm clear NOT sent in before()
✓ clearAfter=true → pm clear sent in after()
✓ pm clear returns "Success" → no error log
✓ pm clear returns other output → error logged, execution continues
```

#### `NetworkLogProcessorTest`
```
✓ {class}-{method}-{ts}-chucker.db → associated with correct TestResult
✓ unmatched filename → error logged, not thrown
✓ multiple files for same test → all associated
```

#### `ExternalIdSelectionStrategyTest`
```
✓ comma-separated IDs → correct TestCase list
✓ ID missing from discovery → excluded silently
✓ @/path/to/file → one ID per line parsed
✓ empty input → empty result
✓ duplicate IDs → deduplicated
✓ all IDs unrecognised → empty list (no exception)
```

#### `TestDiscoveryTest`
```
✓ activeTests, ignoredTests extracted correctly
✓ ClearAppData BEFORE → clearAppDataBefore=true, clearAppDataAfter=false
✓ ClearAppData AFTER → clearAppDataBefore=false, clearAppDataAfter=true
✓ ClearAppData BOTH_BEFORE_AFTER → both=true
✓ ClearLogCat annotation → clearLogcatBefore=true
✓ externalId annotation → TestMetadata.externalId set, byExternalId populated
✓ parameterised class [0] suffix stripped
✓ isTestRunFailed=true → RuntimeException thrown
✓ duplicate externalId → exception (TestRailIdRepeatingException equivalent)
```

#### `OrbitReportRendererTest` (snapshot)
```
✓ known OrbitSummary → data.json matches snapshot
✓ COMPLETED device → correct status field
✓ DISCONNECTED device → disconnectedAt populated
✓ PROVISIONING_FAILED → no tests array, provisioningFailureMessage present
✓ media paths relative (not absolute filesystem paths)
✓ null reportTitle → auto-generated from package + date
```

#### `OrbitCommandTest`
```
✓ TEST_APK positional → File
✓ --serial repeated → Set contains all values
✓ -e KEY VALUE repeated → Map<String, String>
✓ --logcat flag → trackLogcat=true in RunConfig
✓ --external-ids "C1,C2" → ExternalIdSelectionStrategy([C1, C2])
✓ --external-ids @/file → file contents parsed
✓ --method-name without --class-name → UsageError
✓ missing TEST_APK → exit code 2
✓ --always-zero → exitProcess(0) even on test failures
```

---

### Integration Tests — FakeIDevice

No real device or emulator required. A scripted implementation of `IDevice` drives the full orchestration path.

```kotlin
class FakeIDevice(
    val serial: String,
    val installBehavior: InstallBehavior = SUCCESS,
    val scriptedTestEvents: List<FakeTestEvent> = emptyList(),
    val disconnectAfterNTests: Int = Int.MAX_VALUE
) : IDevice {

    override fun installPackage(...) = when (installBehavior) {
        SUCCESS      -> Unit
        FAIL         -> throw InstallException("Fake install failure")
        DISCONNECT   -> throw InstallException("Device disconnected")
    }

    override fun executeShellCommand(cmd: String, receiver: IShellOutputReceiver, ...) {
        // return scripted output based on cmd content
    }
    // ... implement remaining IDevice methods
}

data class FakeTestEvent(
    val testCase: TestCase,
    val outcome: TestStatus,
    val durationMs: Long = 1000L
)
```

#### Integration scenarios

```
✓ single device, 3 tests all pass → COMPLETED, 3 PASS results
✓ single device, 1 fail, 2 pass → COMPLETED, correct statuses
✓ two devices, 4 tests → tests distributed, all 4 results present
✓ device disconnects after N tests → DISCONNECTED, remaining picked up by other device
✓ all devices disconnect → remaining tests absent from results (or IGNORED)
✓ device fails installation → PROVISIONING_FAILED, zero tests run on it
✓ queue availability timeout → worker exits, summary reflects no-result tests
✓ ExternalIdSelectionStrategy with 2 of 5 IDs → only 2 tests executed
✓ trackLogcat=true, test fails → logcat present in TestResult
✓ trackLogcat=true, test passes → logcat absent from TestResult
✓ recordVideo=true, test fails → video path present in TestResult
✓ recordVideo=true, test passes → video path absent from TestResult
```

---

### E2E Tests (CI only — real AVD)

```yaml
# .github/workflows/e2e.yml
jobs:
  e2e:
    runs-on: ubuntu-latest
    steps:
      - uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: 30
          script: ./gradlew :orbit-runner:e2eTest
```

E2E test suite runs Orbit against `test-app` (4 tests: 1 pass, 1 fail, 1 `@Ignore`, 1 `assumeTrue(false)`). Asserts:
- `orbit-report/data.json` exists and is valid JSON
- `summary.{total,passed,failed,ignored}` match expected counts
- The `@Ignore` test has status `IGNORED`
- The `assumeTrue(false)` test has status `ASSUMPTION_FAILURE`
- `orbit-report/index.html` exists
- `orbit-report/junit/TEST-{serial}.xml` exists and contains `<testsuite>`, `<failure>`, `<skipped>`

---

## 15. Dependency Decisions

| Dependency | Replaces | Reason |
|---|---|---|
| `Clikt` | `kotlin-argparser` | More idiomatic Kotlin, better error messages, `@file` expansion, subcommand support |
| `Lombok` | Manual builders / getters | Eliminates all boilerplate on domain classes |
| `MockK` | Mockito | Kotlin-native, cleaner coroutine and object mocking |
| `JUnit 5` | JUnit 4 | `@TempDir`, parameterised tests, better extension model |
| `AssertJ` | raw JUnit asserts | Fluent, readable failure messages |
| React + Vite + TailwindCSS | Mustache HTML renderer | Interactive SPA, no server needed, S3-compatible |
| `Recharts` | — | React-native charting, Gantt + donut |
| `Prism.js` | — | Syntax highlighting for logcat / stacktraces |

### Kept as-is

- DDMLib (`com.android.tools.ddms:ddmlib`) — only ADB abstraction available
- `commons-io` — file utilities
- `guava` — `ImmutableMap`, `Multimap`, `Stopwatch`
- `kotlin-gradle-plugin`
- `shadow` plugin — fat JAR for runner distribution

---

## 16. Gradle Plugin

**Module:** `orbit-gradle-plugin`, plugin id `io.github.rajvinodh.orbit`, versioned in lockstep with `orbit-runner`.

**Goal:** drop Orbit into any Android project without cloning an executor repo, as the old
spoon-standalone-executor required. The team's fixed config lives in **their** project (`orbit.gradle` →
`orbit { }`), and only per-run values come from `-Porbit.<name>`.

### Tasks

| Task | When registered | APKs from | Compiles app code? |
|---|---|---|---|
| `orbitTest` | always | `orbit { testApk / targetApk }` or `-Porbit.testApk / -Porbit.targetApk` | **No** |
| `orbit<Variant>AndroidTest` (e.g. `orbitDebugAndroidTest`) | `com.android.application` applied, variant has androidTest | AGP `SingleArtifact.APK` of the variant + its androidTest | Yes: depends on `package<Variant>` and `package<Variant>AndroidTest` |

For variant tasks, `appPackage`, `testPackage`, `testRunner` and the SDK come from the AGP Variant API. For `orbitTest`
they come from `orbit { }` / `-P`, or are inferred from the test APK (§13, ApkManifestReader).

### Config precedence

`-Porbit.<name>` > `orbit { }` > orbit-runner default. Lists are comma-separated (`-Porbit.devices=a,b`).
`-Porbit.instrumentationArgs=k=v,k2=v2` is **merged** on top of the fixed `orbit { instrumentationArgs }`.
Relative paths resolve against the root project directory.

### Isolated execution

```
OrbitTestTask ──ExecOperations.javaexec──▶ new JVM (classpath = configuration `orbitRunner`)
   buildCliArgs(): task inputs → CLI options      └─ main = io.github.rajvinodh.orbit.cli.OrbitCommandKt
                                                   └─ same path as `java -jar orbit-runner.jar …`
exit code: 0 → print report link · 1 → tests failed (fail build unless ignoreFailures) · other → config/runtime error
```

- **Why a separate JVM and not the Worker API:** a Worker API process shares Gradle's SLF4J binding. `LoggerFactory` returns
  Gradle's logger (the logback cast in `OrbitRunner` failed), and Orbit's `log.info` progress would only show with `--info`.
  A plain `javaexec` gives the CLI's own logback console output, a fresh ADB state per run, and the CLI exit-code contract.
  `OrbitRunner` also checks `instanceof` before touching logback, so embedding it in a host with another binding is safe.
- The plugin has **no** dependency on orbit-runner. The CLI options are the contract between them.

- `orbitRunner` resolves `io.github.rajvinodh:orbit-runner:<runnerVersion>` (default = plugin version), with
  explicit Usage/Category/Bundling=EXTERNAL/LibraryElements attributes so resolution is unambiguous in Android projects.
- **Why isolated:** AGP puts its own ddmlib/guava on the build classpath. orbit-runner pins ddmlib 30.3.0, and loading
  it in-process (as the Spoon buildSrc plugin did) risks version clashes.
- orbit-runner is published as a **thin** jar + POM. The shadow variant is skipped from the `java` component.

### Key invariants

- `orbitTest` has no dependency on compile/assemble tasks: it runs prebuilt APKs only.
- AGP is `compileOnly`. `AndroidVariantWiring` is loaded only inside `pluginManager.withPlugin("com.android.application")`.
- Provider lambdas never touch `Project` at execution time (`GradleOverrides` captures directories up front).
- With ABI/density splits the universal APK is preferred, else the first one.

### Dogfood

`test-app` includes the orbit build in `pluginManagement { includeBuild("..") }`, applies the plugin in `composeApp`, and
keeps its config in `test-app/composeApp/orbit.gradle`, which is the reference for teams.

### Phase 2: Android Studio plugin (planned)

`orbit-intellij` module (IntelliJ Platform Gradle Plugin 2.x). It adds gutter run icons and an "Orbit" run configuration for
`@Test` classes/methods under `src/androidTest`, which run `orbit<Variant>AndroidTest -Porbit.className/methodName`. A
JUnit-style results tree needs a TeamCity-service-message `ITestRunListener` in the runner (pluggable via
`RunConfig.testRunListeners`) feeding `SMTRunnerConsoleView`.
