# Orbit — Contribution Guide

---

## Table of Contents

1. [OOD Class Diagram](#1-ood-class-diagram)
2. [E2E Execution Flowchart](#2-e2e-execution-flowchart)
3. [Understanding Orbit — Reading Guide](#3-understanding-orbit--reading-guide)

---

## 1. OOD Class Diagram

Key classes, their fields/methods, and relationships. Interfaces shown with dashed arrows (`..>`), composition with solid arrows (`-->`).

```mermaid
classDiagram
    direction TB

    %% ── Entry point ──────────────────────────────────────────────────────────
    class OrbitRunner {
        -RunConfig config
        +run() boolean
        -initAdb() AndroidDebugBridge
        -resolveAdbPath() String
        -allPassed(OrbitSummary) boolean
        -logSummary(OrbitSummary)
    }

    %% ── Configuration ────────────────────────────────────────────────────────
    class RunConfig {
        +File testApk
        +List~File~ otherApks
        +String appPackage
        +String testPackage
        +String testRunner
        +Set~String~ serials
        +Set~String~ skipSerials
        +Duration adbTimeout
        +boolean grantAll
        +boolean trackLogcat
        +boolean recordVideo
        +boolean trackNetworkLogs
        +boolean codeCoverage
        +RetryPolicy retryPolicy
        +TestSelectionStrategy selectionStrategy
        +File output
        +File androidSdk
        +effectiveTestPackage() String
        +logcatPriority(String)$ int
    }

    class ApkManifestReader {
        <<utility>>
        +readInstrumentation(File)$ InstrumentationInfo
        +fillMissing(RunConfig)$ RunConfig
    }

    %% ── Gradle plugin (orbit-gradle-plugin) ──────────────────────────────────
    class OrbitPlugin {
        +apply(Project)
        -createRunnerConfiguration() Configuration
        -registerPrebuiltTask() orbitTest
        +configureCommon(OrbitTestTask, OrbitExtension, GradleOverrides, Configuration)$
    }
    class OrbitExtension {
        <<orbit DSL>>
        +RegularFileProperty testApk
        +RegularFileProperty targetApk
        +Property~String~ executionMode
        +MapProperty instrumentationArgs
        +Property~Boolean~ ignoreFailures
        +Property~String~ runnerVersion
    }
    class GradleOverrides {
        +string(name) Provider
        +bool(name) Provider
        +list(name) Provider
        +map(name) Provider
        +regularFile(name) Provider
    }
    class AndroidVariantWiring {
        +register(Project, OrbitExtension, GradleOverrides, Configuration)
        -apkIn(Directory, BuiltArtifactsLoader) RegularFile
    }
    class OrbitTestTask {
        +RegularFileProperty testApk
        +RegularFileProperty targetApk
        +ConfigurableFileCollection runnerClasspath
        +runOrbit()
        -buildCliArgs() List~String~
    }
    class OrbitCommand {
        <<CLI main, own JVM>>
        +run()
    }

    %% ── Orchestration ────────────────────────────────────────────────────────
    class MultiDeviceOrchestrator {
        -RunConfig config
        -DeviceProvisioner provisioner
        -ExecutorService executor
        +run(AndroidDebugBridge) OrbitSummary
        -runDiscovery(Set, AndroidDebugBridge) DiscoveryResult
        -provisionAll(Set, AndroidDebugBridge) Map
        -buildPipeline() TestExecutionPipeline
        -resolveSerials(AndroidDebugBridge) Set
        -findOnlineDevice(AndroidDebugBridge, String) IDevice
        -awaitUninterruptibly(CountDownLatch)
    }

    class DeviceOrchestrator {
        -RunConfig config
        -TestExecutionPipeline pipeline
        -IDevice device
        -DeviceSession completedSession
        +getDevice() IDevice
        +startSession(IDevice) DeviceSession
        +execute(IDevice, TestCase, TestMetadata, DeviceSession)
        +endSession(IDevice, DeviceSession)
        +collectAndBuild(AndroidDebugBridge) DeviceResult
        -createRunner(IDevice, TestCase) RemoteAndroidTestRunner
        -buildCollectors(String, File) List
        -isDisconnect(Exception) boolean
    }

    class DeviceWorker {
        -IDevice device
        -BlockingQueue~TestCase~ queue
        -DeviceOrchestrator orchestrator
        -DiscoveryResult discovery
        -CountDownLatch doneLatch
        +run()
    }

    class DeviceSession {
        -String serial
        -MulticastTestRunListener multicast
        +addTestResult(TestCase, TestResult)
        +markDisconnected()
        +addException(String)
        +getMulticast() MulticastTestRunListener
        +build() DeviceResult
    }

    %% ── Discovery ────────────────────────────────────────────────────────────
    class TestDiscovery {
        -RunConfig config
        +run(IDevice) DiscoveryResult
        -createLogRunner(IDevice) RemoteAndroidTestRunner
        -buildDiscoveryResult(LogRecordingTestRunListener) DiscoveryResult
        -buildMetadata(TestCase, TestIdentifier, LogRecordingTestRunListener) TestMetadata
    }

    class LogRecordingTestRunListener {
        -List~TestIdentifier~ activeTests
        -List~TestIdentifier~ ignoredTests
        -Map annotations
        -boolean testRunFailed
        +activeTests() List
        +ignoredTests() List
        +annotationsFor(TestIdentifier) List
        +externalIdFor(TestIdentifier) String
        +clearAppDataActionFor(TestIdentifier) String
        +isTestRunFailed() Boolean
    }

    %% ── Execution ────────────────────────────────────────────────────────────
    class TestExecutionPipeline {
        -List~PreTestAction~ preActions
        -List~PostTestAction~ postActions
        -RetryPolicy retryPolicy
        +execute(IDevice, TestExecutionContext, RemoteAndroidTestRunner, Collection, RecentTestOutcomeProvider)
    }

    class TestExecutionContext {
        +TestCase testCase
        +TestMetadata metadata
        +String deviceSerial
        +String externalStorage
        +int attemptNumber
        +withAttemptNumber(int) TestExecutionContext
    }

    class AppDataCleaner {
        -String appPackage
        +before(IDevice, TestExecutionContext)
        +after(IDevice, TestExecutionContext, boolean)
    }

    class VideoRecorder {
        +before(IDevice, TestExecutionContext)
        +after(IDevice, TestExecutionContext, boolean)
        -stopAndWaitForCompletion(IDevice, TestExecutionContext)
        -isFileCompleted(IDevice, TestExecutionContext) boolean
        -deleteVideo(IDevice, TestExecutionContext)
    }

    class LogcatClearer {
        +before(IDevice, TestExecutionContext)
    }

    class NetworkLogPuller {
        -File outputDir
        +after(IDevice, TestExecutionContext, boolean)
    }

    %% ── Listeners ────────────────────────────────────────────────────────────
    class OrbitTestRunListener {
        -Map~TestIdentifier, TestResult.Builder~ inProgress
        -boolean recentTestFailed
        -TestResult lastResult
        -String runFailureMessage
        +testStarted(TestIdentifier)
        +testFailed(TestIdentifier, String)
        +testAssumptionFailure(TestIdentifier, String)
        +testIgnored(TestIdentifier)
        +testEnded(TestIdentifier, Map)
        +testRunFailed(String)
        +didRecentTestFail() boolean
        +getLastResult() TestResult
        +getRunFailureMessage() String
    }

    class MulticastTestRunListener {
        -List~ITestRunListener~ delegates
        +multiRunStarted(String, int)
        +multiRunEnded()
        +testStarted(TestIdentifier)
        +testFailed(TestIdentifier, String)
        +testEnded(TestIdentifier, Map)
        +testRunFailed(String)
    }

    %% ── Provisioning ─────────────────────────────────────────────────────────
    class DefaultDeviceProvisioner {
        -AndroidDebugBridge adb
        -DeviceConnectionChecker connectionChecker
        +provision(IDevice, RunConfig) ProvisionResult
        -installApks(IDevice, RunConfig) boolean
        -grantPermissions(IDevice, RunConfig) boolean
        -cleanDeviceDirectories(IDevice) boolean
        -resolveFailure(IDevice, RunConfig) ProvisionResult
    }

    class DeviceConnectionChecker {
        +isAvailable(AndroidDebugBridge, String) boolean
    }

    %% ── Domain ───────────────────────────────────────────────────────────────
    class DiscoveryResult {
        +List~TestCase~ activeTests
        +List~TestCase~ ignoredTests
        +Map~TestCase, TestMetadata~ metadata
        +Map~String, TestCase~ byExternalId
    }

    class TestCase {
        +String className
        +String methodName
        +displayName() String
        +from(TestIdentifier) TestCase
    }

    class TestMetadata {
        +TestCase testCase
        +boolean clearAppDataBefore
        +boolean clearAppDataAfter
        +boolean clearLogcatBefore
        +String externalId
    }

    class TestResult {
        +TestCase testCase
        +TestStatus status
        +long startedAt
        +long durationMs
        +List~File~ screenshots
        +List~File~ videos
        +String externalId
        +isFailed() boolean
        +isPassed() boolean
    }

    class DeviceResult {
        +String serial
        +DeviceStatus status
        +long startedAt
        +long durationMs
        +Map~TestCase, TestResult~ testResults
        +List exceptions
    }

    class OrbitSummary {
        +String title
        +long startedAt
        +long durationMs
        +Map~String, DeviceResult~ results
        +List~TestCase~ missedTests
        +String orbitVersion
    }

    %% ── Interfaces ───────────────────────────────────────────────────────────
    class PreTestAction {
        <<interface>>
        +before(IDevice, TestExecutionContext)
    }

    class PostTestAction {
        <<interface>>
        +after(IDevice, TestExecutionContext, boolean)
    }

    class DeviceProvisioner {
        <<interface>>
        +provision(IDevice, RunConfig) ProvisionResult
    }

    class RetryPolicy {
        <<interface>>
        +shouldRetry(TestExecutionContext, TestResult) boolean
        NONE
    }

    class TestSelectionStrategy {
        <<interface>>
        +select(DiscoveryResult) List~TestCase~
        ALL
    }

    class RecentTestOutcomeProvider {
        <<interface>>
        +didRecentTestFail() boolean
        +getLastResult() TestResult
    }

    %% ── Relationships ────────────────────────────────────────────────────────
    OrbitRunner --> RunConfig
    OrbitRunner --> MultiDeviceOrchestrator : creates

    MultiDeviceOrchestrator --> RunConfig
    MultiDeviceOrchestrator --> DeviceProvisioner
    MultiDeviceOrchestrator --> TestDiscovery : creates
    MultiDeviceOrchestrator --> DeviceOrchestrator : creates
    MultiDeviceOrchestrator --> DeviceWorker : submits to executor
    MultiDeviceOrchestrator --> OrbitSummary : builds

    DeviceWorker --> DeviceOrchestrator : delegates
    DeviceWorker --> DiscoveryResult : reads metadata
    DeviceWorker --> DeviceSession : manages

    DeviceOrchestrator --> RunConfig
    DeviceOrchestrator --> TestExecutionPipeline
    DeviceOrchestrator --> DeviceSession : creates
    DeviceOrchestrator --> OrbitTestRunListener : creates per test
    DeviceOrchestrator --> DeviceResult : builds via session

    DeviceSession --> MulticastTestRunListener
    DeviceSession --> DeviceResult : builds

    TestDiscovery --> RunConfig
    TestDiscovery --> LogRecordingTestRunListener : creates
    TestDiscovery --> DiscoveryResult : returns

    TestExecutionPipeline --> RetryPolicy
    TestExecutionPipeline --> PreTestAction : calls before
    TestExecutionPipeline --> PostTestAction : calls after

    AppDataCleaner ..|> PreTestAction
    AppDataCleaner ..|> PostTestAction
    VideoRecorder ..|> PreTestAction
    VideoRecorder ..|> PostTestAction
    LogcatClearer ..|> PreTestAction
    NetworkLogPuller ..|> PostTestAction

    DefaultDeviceProvisioner ..|> DeviceProvisioner
    DefaultDeviceProvisioner --> DeviceConnectionChecker

    OrbitTestRunListener ..|> RecentTestOutcomeProvider
    MulticastTestRunListener --> OrbitTestRunListener : delegates to

    DiscoveryResult --> TestCase
    DiscoveryResult --> TestMetadata
    DeviceResult --> TestResult
    OrbitSummary --> DeviceResult
    TestResult --> TestCase
    TestMetadata --> TestCase

    RunConfig --> RetryPolicy
    RunConfig --> TestSelectionStrategy
    OrbitRunner --> ApkManifestReader : fillMissing()

    OrbitPlugin --> OrbitExtension : creates
    OrbitPlugin --> OrbitTestTask : registers orbitTest
    OrbitPlugin --> AndroidVariantWiring : with com.android.application
    AndroidVariantWiring --> OrbitTestTask : registers orbitVariantAndroidTest
    OrbitTestTask --> GradleOverrides : -Porbit overrides the orbit DSL
    OrbitTestTask --> OrbitCommand : javaexec CLI args, exit code
    OrbitCommand --> RunConfig : builds
    OrbitCommand --> OrbitRunner : run()
```

---

## 2. E2E Execution Flowchart

End-to-end flow from `./gradlew :orbit-runner:e2eTest` (or CLI) through all 5 phases to report output.

```mermaid
flowchart TD
    START([E2ETest @BeforeAll\nor OrbitCommand.run]) --> OR[OrbitRunner.run]

    OR --> ADB[initAdb\nAndroidDebugBridge.init\nwait for device list — 5s]
    ADB --> ADB_OK{ADB connected?}
    ADB_OK -- No --> FAIL_ADB([return false])
    ADB_OK -- Yes --> MDO[MultiDeviceOrchestrator.run]

    MDO --> RS[resolveSerials\nauto-detect ONLINE devices\nor use config serials]

    subgraph PHASE1 ["Phase 1 — Discovery (sequential, 1 device)"]
        RS --> D1[TestDiscovery.run\nfirst online device]
        D1 --> D2[RemoteAndroidTestRunner\n-e log true\nadbTimeout = 30s in E2E]
        D2 --> D3[LogRecordingTestRunListener\ncollects test list + annotations]
        D3 --> D4[DiscoveryResult\nactiveTests, metadata, byExternalId]
        D4 --> D5[TestSelectionStrategy.select\nALL or ExternalIdSelectionStrategy]
    end

    D5 --> MISSED[tests in discovery\nnot selected → missedTests]

    subgraph PHASE2 ["Phase 2 — Provision (parallel, CountDownLatch)"]
        MISSED --> P1[DefaultDeviceProvisioner.provision\nper device — in executor thread]
        P1 --> P2[installApks\ntest APK + app APK]
        P2 --> P3[cleanDeviceDirectories\norbit-screenshots, orbit-files]
        P3 --> P4[grantPermissions\nREAD/WRITE_EXTERNAL_STORAGE etc]
        P4 --> P5{ProvisionResult}
        P5 -- SUCCESS --> P6[DeviceOrchestrator created\nTestExecutionPipeline built]
        P5 -- FAILED/DISCONNECTED --> P7[device skipped\nDeviceResult status=PROVISIONING_FAILED]
    end

    subgraph PHASE3 ["Phase 3 — Execute (parallel, BlockingQueue)"]
        P6 --> EX1[BlockingQueue pre-filled\nwith all testsToRun]
        EX1 --> EX2[DeviceWorker per device\nsubmitted to CachedThreadPool]
        EX2 --> EX3[queue.poll\nnext TestCase]
        EX3 --> EX_EMPTY{Queue empty?}
        EX_EMPTY -- Yes --> EX_DONE[doneLatch.countDown\nworker exits]
        EX_EMPTY -- No --> EX4[TestExecutionPipeline.execute]

        EX4 --> PRE[PreTestActions\nAppDataCleaner before\nLogcatClearer\nVideoRecorder start]
        PRE --> RUN[RemoteAndroidTestRunner.run\nadb shell am instrument\nper-test — adbTimeout applied]
        RUN --> POST[PostTestActions\nVideoRecorder stop+poll\nNetworkLogPuller if failed]
        POST --> RETRY{RetryPolicy\n.shouldRetry?}
        RETRY -- Yes --> PRE
        RETRY -- No --> DISC{DeviceDisconnected?}
        DISC -- Yes --> MARK[session.markDisconnected\nworker returns\nremaining tests stay in queue]
        DISC -- No --> EX3
    end

    EX_DONE --> AWAIT[awaitUninterruptibly\nexecutionDone latch]

    subgraph PHASE4 ["Phase 4 — Collect artifacts (parallel, CountDownLatch)"]
        AWAIT --> C1[ScreenshotProcessor\npull orbit-screenshots/ → images/serial/]
        AWAIT --> C2[VideoProcessor\nmove mp4 → videos/serial/]
        AWAIT --> C3[LogcatMapper\nmap logs to failed tests only]
        AWAIT --> C4[NetworkLogProcessor\nmap chucker .db by filename]
        AWAIT --> C5[CoverageCollector\npull coverage.ec if enabled]
        C1 & C2 & C3 & C4 & C5 --> C6[DeviceResult built\nvia DeviceSession.build]
    end

    subgraph PHASE5 ["Phase 5 — Report"]
        C6 --> R1[OrbitReportRenderer.render\nbuild data.json from OrbitSummary]
        R1 --> R2[data.json written\nto orbit-report/]
        R1 --> R3[index.html + assets\ncopied from JAR resources]
        R2 & R3 --> R4[ReportPublisher.publish\ndefault = FileSystemPublisher no-op]
    end

    R4 --> SUM[OrbitSummary returned\nto OrbitRunner]
    SUM --> PASS{allPassed?}
    PASS -- Yes --> TERM_OK[AndroidDebugBridge.terminate\nreturn true]
    PASS -- No --> TERM_FAIL[AndroidDebugBridge.terminate\nreturn false]

    TERM_OK --> END_OK([E2ETest assertions pass\nor CLI exit 0])
    TERM_FAIL --> END_FAIL([E2ETest check fails\nor CLI exit 1])
```

---

## 3. Understanding Orbit — Reading Guide

Read in this order — each step builds on the last.

### Step 1 — Config contract (`RunConfig.java`)
Everything Orbit can do is expressed as a field here. Read it top to bottom once. You'll have a mental map of every feature before reading any logic.

### Step 2 — Entry point (`OrbitRunner.java`)
Seven methods, under 150 lines. Shows ADB lifecycle and delegates all work to `MultiDeviceOrchestrator`. Nothing surprising.

### Step 3 — The 5-phase mental model (`MultiDeviceOrchestrator.java`)
The `run()` method is the skeleton of the whole system:
```
Phase 1  resolveSerials → runDiscovery
Phase 2  provisionAll   (parallel CountDownLatch)
Phase 3  DeviceWorkers  (parallel BlockingQueue)
Phase 4  collectAndBuild (parallel CountDownLatch)
Phase 5  report
```
Understand this and you understand the system. Every other class is an implementation detail of one phase.

### Step 4 — Concurrency model (`DeviceWorker.java`)
`queue.poll()` with no timeout. Workers drain the shared queue and exit when it's empty. Device disconnect → worker exits, remaining tests stay in queue for other workers.

### Step 5 — Test discovery (`TestDiscovery.java` + `LogRecordingTestRunListener.kt`)
Phase 1 is just `adb shell am instrument -e log true`. The listener records what the runner reports without executing anything. Result: typed `DiscoveryResult` with metadata per test.

### Step 6 — Per-test execution (`TestExecutionPipeline.java`)
Pre-actions → `runner.run()` → post-actions → retry check. The retry loop is the only non-trivial control flow. Actions and retry policy are all pluggable.

### Step 7 — Extension points (Section 5 in orbit-design.md)
`PreTestAction`, `PostTestAction`, `ArtifactCollector`, `RetryPolicy`, `TestSelectionStrategy`, `ReportPublisher`. Adding a feature = implementing one interface, wiring it in `RunConfig` or `buildPipeline()`. No existing class needs to change.

### Key invariants to keep in mind
- `RunConfig` is immutable — built once at entry, passed everywhere by reference
- `DeviceSession` is the only mutable state per device — lives Phase 2 → Phase 4
- `adbTimeout` must be applied to every `RemoteAndroidTestRunner` via `setMaxTimeToOutputResponse()` — see `TestDiscovery.createLogRunner()` and `DeviceOrchestrator.createRunner()`
- `queue.poll()` (no timeout) — workers exit immediately when the queue is empty; do not add a timeout here