package io.github.rajvinodh.orbit.domain;

import com.android.ddmlib.logcat.LogCatMessage;
import java.io.File;
import java.util.List;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.jetbrains.annotations.Nullable;

@Value
@Builder(toBuilder = true)
public class TestResult {
  TestCase testCase;
  TestStatus status;
  @Nullable String exception; // raw stack trace string
  long startedAt; // epoch ms — required for Gantt timeline
  long durationMs;
  @Singular List<File> screenshots;
  @Singular List<File> videos;
  @Singular List<File> files;

  @Singular("logcatMessage")
  List<LogCatMessage> logcat; // populated only on FAIL

  @Singular("crashMessage")
  List<LogCatMessage> crashLog; // AndroidRuntime tag only

  @Nullable String externalId;
  @Nullable String ignoredReason;

  public boolean isCrashed() {
    return !crashLog.isEmpty();
  }

  public boolean isPassed() {
    return status == TestStatus.PASS;
  }

  public boolean isFailed() {
    return status == TestStatus.FAIL;
  }
}
