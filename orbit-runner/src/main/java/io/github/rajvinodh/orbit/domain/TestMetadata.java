package io.github.rajvinodh.orbit.domain;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.Nullable;

@Value
@Builder
public class TestMetadata {
  TestCase testCase;
  boolean clearAppDataBefore;
  boolean clearAppDataAfter;
  boolean clearLogcatBefore;
  @Nullable String externalId;
}
