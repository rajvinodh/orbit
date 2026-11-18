package io.github.rajvinodh.orbit.execution.actions;

import com.android.ddmlib.IDevice;
import io.github.rajvinodh.orbit.execution.TestExecutionContext;

@FunctionalInterface
public interface PreTestAction {
  void before(IDevice device, TestExecutionContext ctx) throws Exception;
}
