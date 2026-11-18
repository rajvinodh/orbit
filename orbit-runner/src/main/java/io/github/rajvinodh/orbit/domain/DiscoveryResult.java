package io.github.rajvinodh.orbit.domain;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

@Value
@Builder
public class DiscoveryResult {
  List<TestCase> activeTests;
  List<TestCase> ignoredTests;

  @Singular("metadataEntry")
  Map<TestCase, TestMetadata> metadata;

  @Singular("byExternalIdEntry")
  Map<String, TestCase> byExternalId;
}
