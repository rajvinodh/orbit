package io.github.rajvinodh.orbit.listener;

import com.android.ddmlib.testrunner.ITestRunListener;
import com.android.ddmlib.testrunner.TestIdentifier;
import io.github.rajvinodh.orbit.domain.TestCase;
import io.github.rajvinodh.orbit.domain.TestResult;
import io.github.rajvinodh.orbit.domain.TestStatus;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * Tracks per-test state during a single instrumentation run. Uses a plain inner holder to avoid
 * referencing Lombok-generated Builder as a field type.
 */
public final class OrbitTestRunListener implements ITestRunListener, RecentTestOutcomeProvider {

  private final Map<TestIdentifier, InProgressTest> inProgress = new HashMap<>();
  private boolean recentTestFailed = false;
  @Nullable private TestResult lastResult;
  @Nullable private String runFailureMessage;

  @Override
  public void testStarted(TestIdentifier test) {
    inProgress.put(test, new InProgressTest(TestCase.from(test)));
    recentTestFailed = false;
  }

  @Override
  public void testFailed(TestIdentifier test, String trace) {
    InProgressTest t = inProgress.get(test);
    if (t != null) {
      t.status = TestStatus.FAIL;
      t.exception = trace;
    }
    recentTestFailed = true;
  }

  @Override
  public void testAssumptionFailure(TestIdentifier test, String trace) {
    InProgressTest t = inProgress.get(test);
    if (t != null) {
      t.status = TestStatus.ASSUMPTION_FAILURE;
      t.exception = trace;
    }
    recentTestFailed = true;
  }

  @Override
  public void testIgnored(TestIdentifier test) {
    inProgress.computeIfAbsent(test, id -> new InProgressTest(TestCase.from(id))).status =
        TestStatus.IGNORED;
  }

  @Override
  public void testEnded(TestIdentifier test, Map<String, String> metrics) {
    InProgressTest t = inProgress.remove(test);
    if (t == null) return;
    long duration = System.currentTimeMillis() - t.startedAt;
    lastResult =
        TestResult.builder()
            .testCase(t.testCase)
            .startedAt(t.startedAt)
            .durationMs(duration)
            .status(t.status)
            .exception(t.exception)
            .build();
  }

  @Override
  public void testRunFailed(String errorMessage) {
    runFailureMessage = errorMessage;
    recentTestFailed = true;
  }

  @Override
  public void testRunStarted(String runName, int testCount) {}

  @Override
  public void testRunStopped(long elapsedTime) {}

  @Override
  public void testRunEnded(long elapsedTime, Map<String, String> runMetrics) {}

  @Override
  public boolean didRecentTestFail() {
    return recentTestFailed;
  }

  @Override
  public TestResult getLastResult() {
    return lastResult;
  }

  @Nullable
  public String getRunFailureMessage() {
    return runFailureMessage;
  }

  private static class InProgressTest {
    final TestCase testCase;
    final long startedAt = System.currentTimeMillis();
    TestStatus status = TestStatus.PASS;
    @Nullable String exception;

    InProgressTest(TestCase testCase) {
      this.testCase = testCase;
    }
  }
}
