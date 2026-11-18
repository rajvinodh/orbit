package io.github.rajvinodh.orbit.execution.actions;

import com.android.ddmlib.CollectingOutputReceiver;
import com.android.ddmlib.IDevice;
import io.github.rajvinodh.orbit.execution.TestExecutionContext;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AppDataCleaner implements PreTestAction, PostTestAction {

  private final String appPackage;

  public AppDataCleaner(String appPackage) {
    this.appPackage = appPackage;
  }

  @Override
  public void before(IDevice device, TestExecutionContext ctx) throws Exception {
    if (ctx.getMetadata().isClearAppDataBefore()) {
      pmClear(device, ctx);
    }
  }

  @Override
  public void after(IDevice device, TestExecutionContext ctx, boolean testFailed) throws Exception {
    if (ctx.getMetadata().isClearAppDataAfter()) {
      pmClear(device, ctx);
    }
  }

  private void pmClear(IDevice device, TestExecutionContext ctx) throws Exception {
    CollectingOutputReceiver receiver = new CollectingOutputReceiver();
    device.executeShellCommand("pm clear " + appPackage, receiver, 30, TimeUnit.SECONDS);
    String output = receiver.getOutput().trim();
    if (!output.equals("Success")) {
      log.error(
          "pm clear for {} on {} returned unexpected output: {}",
          ctx.getTestCase().displayName(),
          device.getSerialNumber(),
          output);
    }
  }
}
