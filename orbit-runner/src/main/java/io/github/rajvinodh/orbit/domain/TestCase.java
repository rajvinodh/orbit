package io.github.rajvinodh.orbit.domain;

import com.android.ddmlib.testrunner.TestIdentifier;
import lombok.Value;

@Value
public class TestCase {
  String className;
  String methodName;

  public String displayName() {
    return className + "#" + methodName;
  }

  public static TestCase from(TestIdentifier id) {
    return new TestCase(id.getClassName(), id.getTestName());
  }
}
