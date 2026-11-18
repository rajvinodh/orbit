package io.github.rajvinodh.orbit.execution.actions;

import com.android.ddmlib.IDevice;
import io.github.rajvinodh.orbit.execution.TestExecutionContext;

@FunctionalInterface
public interface PostTestAction {
  void after(IDevice device, TestExecutionContext ctx, boolean testFailed) throws Exception;
}
