package io.github.rajvinodh.orbit.selection;

import io.github.rajvinodh.orbit.domain.DiscoveryResult;
import io.github.rajvinodh.orbit.domain.TestCase;
import java.util.ArrayList;
import java.util.List;

@FunctionalInterface
public interface TestSelectionStrategy {
  TestSelectionStrategy ALL = discovery -> new ArrayList<>(discovery.getActiveTests());

  List<TestCase> select(DiscoveryResult discovery);
}
