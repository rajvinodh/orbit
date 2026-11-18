package io.github.rajvinodh.orbit.listener;

import io.github.rajvinodh.orbit.domain.TestResult;

public interface RecentTestOutcomeProvider {
  boolean didRecentTestFail();

  TestResult getLastResult();
}
