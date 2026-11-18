package io.github.rajvinodh.orbit.execution.actions;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellOutputReceiver;
import io.github.rajvinodh.orbit.domain.TestCase;
import io.github.rajvinodh.orbit.execution.TestExecutionContext;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CoveragePuller implements PostTestAction {

  private final File coverageOutputDir;
  private final String appPackage;

  public CoveragePuller(File coverageOutputDir, String instrumentationPackage) {
    this.coverageOutputDir = coverageOutputDir;
    this.appPackage = instrumentationPackage;
  }

  @Override
  public void after(IDevice device, TestExecutionContext ctx, boolean testFailed) {
    String devicePath = "/data/data/" + appPackage + "/files/coverage.ec";
    String testId = sanitize(ctx.getTestCase());
    File localDir = new File(coverageOutputDir, device.getSerialNumber());
    localDir.mkdirs();
    File localFile = new File(localDir, testId + ".ec");

    // Private storage can't be pulled with adb sync — use run-as cat with a binary receiver.
    String cmd = "run-as " + appPackage + " cat " + devicePath;
    try (FileOutputStream fos = new FileOutputStream(localFile)) {
      device.executeShellCommand(cmd, new BinaryFileReceiver(fos), 30, TimeUnit.SECONDS);
      log.debug("Pulled coverage for {} ({} bytes)", testId, localFile.length());
    } catch (Exception e) {
      log.warn("Could not pull coverage for {}: {}", testId, e.getMessage());
      localFile.delete();
    }
  }

  public static String sanitize(TestCase test) {
    return test.getClassName().replace('.', '_') + "_" + test.getMethodName();
  }

  private static final class BinaryFileReceiver implements IShellOutputReceiver {
    private final FileOutputStream fos;

    BinaryFileReceiver(FileOutputStream fos) {
      this.fos = fos;
    }

    @Override
    public void addOutput(byte[] data, int offset, int length) {
      try {
        fos.write(data, offset, length);
      } catch (IOException e) {
        /* handled by outer catch */
      }
    }

    @Override
    public void flush() {}

    @Override
    public boolean isCancelled() {
      return false;
    }
  }
}
