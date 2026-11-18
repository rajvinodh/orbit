package io.github.rajvinodh.orbit.execution.actions;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.SyncException;
import io.github.rajvinodh.orbit.execution.TestExecutionContext;
import java.io.File;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NetworkLogPuller implements PostTestAction {

  private static final String DEVICE_DB_DIR = "/sdcard/app_orbit-network";

  private final File outputDir;

  public NetworkLogPuller(File outputDir) {
    this.outputDir = outputDir;
  }

  @Override
  public void after(IDevice device, TestExecutionContext ctx, boolean testFailed) throws Exception {
    if (!testFailed) return;

    String className = ctx.getTestCase().getClassName();
    String methodName = ctx.getTestCase().getMethodName();
    String filename =
        className + "-" + methodName + "-" + System.currentTimeMillis() + "-chucker.db";
    String devicePath = DEVICE_DB_DIR + "/" + filename;

    File destDir = new File(outputDir, ctx.getDeviceSerial());
    destDir.mkdirs();

    try {
      device.pullFile(devicePath, new File(destDir, filename).getAbsolutePath());
    } catch (SyncException e) {
      log.warn(
          "Could not pull Chucker DB for {}: {}", ctx.getTestCase().displayName(), e.getMessage());
    }
  }
}
