package io.github.rajvinodh.orbit.execution;

import io.github.rajvinodh.orbit.domain.TestResult;

@FunctionalInterface
public interface RetryPolicy {
  RetryPolicy NONE = (ctx, result) -> false;

  boolean shouldRetry(TestExecutionContext ctx, TestResult result);
}
