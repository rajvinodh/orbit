package io.github.rajvinodh.orbit.execution.actions;

import com.android.ddmlib.CollectingOutputReceiver;
import com.android.ddmlib.IDevice;
import io.github.rajvinodh.orbit.execution.TestExecutionContext;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LogcatClearer implements PreTestAction {

  @Override
  public void before(IDevice device, TestExecutionContext ctx) throws Exception {
    if (ctx.getMetadata().isClearLogcatBefore()) {
      device.executeShellCommand("logcat -c", new CollectingOutputReceiver(), 15, TimeUnit.SECONDS);
    }
  }
}
