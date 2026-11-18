package io.github.rajvinodh.orbit.domain;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

@Value
@Builder
public class OrbitSummary {
  String title;
  long startedAt;
  long durationMs;
  @Singular Map<String, DeviceResult> results;
  @Singular List<TestCase> missedTests;
  String orbitVersion;
  @Builder.Default ExecutionMode executionMode = ExecutionMode.DISTRIBUTE;
}
