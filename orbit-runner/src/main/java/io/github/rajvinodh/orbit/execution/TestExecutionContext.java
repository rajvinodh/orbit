package io.github.rajvinodh.orbit.execution;

import io.github.rajvinodh.orbit.domain.TestCase;
import io.github.rajvinodh.orbit.domain.TestMetadata;
import lombok.Builder;
import lombok.Value;
import lombok.With;

@Value
@Builder
public class TestExecutionContext {
  TestCase testCase;
  TestMetadata metadata;
  String deviceSerial;
  String externalStorage;
  @With @Builder.Default int attemptNumber = 0;
}
