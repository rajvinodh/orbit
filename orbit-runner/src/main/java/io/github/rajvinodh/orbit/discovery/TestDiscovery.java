package io.github.rajvinodh.orbit.discovery;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.TestIdentifier;
import io.github.rajvinodh.orbit.config.RunConfig;
import io.github.rajvinodh.orbit.discovery.internal.LogRecordingTestRunListener;
import io.github.rajvinodh.orbit.domain.DiscoveryResult;
import io.github.rajvinodh.orbit.domain.TestCase;
import io.github.rajvinodh.orbit.domain.TestMetadata;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TestDiscovery {

  private final RunConfig config;

  public TestDiscovery(RunConfig config) {
    this.config = config;
  }

  public DiscoveryResult run(IDevice device) throws Exception {
    LogRecordingTestRunListener recorder = new LogRecordingTestRunListener();
    RemoteAndroidTestRunner runner = createLogRunner(device);
    runner.addBooleanArg("log", true);
    runner.addInstrumentationArg(
        "listener", "io.github.rajvinodh.orbit.client.OrbitMetadataListener");
    runner.run(recorder);

    if (Boolean.TRUE.equals(recorder.isTestRunFailed())) {
      throw new RuntimeException("Discovery dry-run failed: " + recorder.runName());
    }

    return buildDiscoveryResult(recorder);
  }

  private RemoteAndroidTestRunner createLogRunner(IDevice device) {
    RemoteAndroidTestRunner runner =
        new RemoteAndroidTestRunner(config.effectiveTestPackage(), config.getTestRunner(), device);
    runner.setMaxTimeToOutputResponse(
        config.getAdbTimeout().toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
    if (config.getClassName() != null) runner.setClassName(config.getClassName());
    if (config.getMethodName() != null)
      runner.setMethodName(config.getClassName(), config.getMethodName());
    config.getInstrumentationArgs().forEach(runner::addInstrumentationArg);
    return runner;
  }

  // Package-private for unit testing
  DiscoveryResult buildDiscoveryResult(LogRecordingTestRunListener recorder) {
    if (Boolean.TRUE.equals(recorder.isTestRunFailed())) {
      throw new RuntimeException("Discovery dry-run failed: " + recorder.runName());
    }
    List<TestCase> active =
        recorder.activeTests().stream().map(TestCase::from).collect(Collectors.toList());
    List<TestCase> ignored =
        recorder.ignoredTests().stream().map(TestCase::from).collect(Collectors.toList());

    Map<TestCase, TestMetadata> metadata = new LinkedHashMap<>();
    Map<String, TestCase> byExternalId = new LinkedHashMap<>();

    for (TestIdentifier id : recorder.activeTests()) {
      TestCase test = TestCase.from(id);
      TestMetadata meta = buildMetadata(test, id, recorder);
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

  private TestMetadata buildMetadata(
      TestCase test, TestIdentifier id, LogRecordingTestRunListener recorder) {
    List<String> annotations = recorder.annotationsFor(id);
    String clearAction = recorder.clearAppDataActionFor(id);
    String externalId = recorder.externalIdFor(id);

    return TestMetadata.builder()
        .testCase(test)
        .clearAppDataBefore(clearAction != null && clearAction.contains("BEFORE"))
        .clearAppDataAfter(clearAction != null && clearAction.contains("AFTER"))
        .clearLogcatBefore(annotations.contains("ClearLogCat"))
        .externalId(externalId)
        .build();
  }
}
