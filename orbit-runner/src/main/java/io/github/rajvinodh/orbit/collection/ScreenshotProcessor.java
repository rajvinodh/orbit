package io.github.rajvinodh.orbit.collection;

import com.android.ddmlib.IDevice;
import io.github.rajvinodh.orbit.domain.TestCase;
import io.github.rajvinodh.orbit.orchestration.DeviceSession;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

/**
 * Pulls app_orbit-screenshots/ from device and moves them to
 * orbit-report/images/{serial}/{class}/{method}/.
 */
@Slf4j
public class ScreenshotProcessor implements ArtifactCollector {

  private static final String DEVICE_SCREENSHOT_DIR = "/sdcard/app_orbit-screenshots";

  private final File imageOutputDir; // orbit-report/images/{serial}/

  public ScreenshotProcessor(File imageOutputDir) {
    this.imageOutputDir = imageOutputDir;
  }

  @Override
  public void collect(IDevice device, DeviceSession session) throws IOException {
    File localPullDir = new File(imageOutputDir, ".pull-tmp");
    pullFromDevice(device, localPullDir);
    processLocalDir(localPullDir, session);
    FileUtils.deleteQuietly(localPullDir);
  }

  private void pullFromDevice(IDevice device, File dest) {
    DevicePuller.pullDirectory(device, DEVICE_SCREENSHOT_DIR, dest);
  }

  private void processLocalDir(File pullDir, DeviceSession session) throws IOException {
    File screenshotRoot = new File(pullDir, "app_orbit-screenshots");
    if (!screenshotRoot.exists()) return;

    File[] classDirs = screenshotRoot.listFiles(File::isDirectory);
    if (classDirs == null) return;

    for (File classDir : classDirs) {
      String className = classDir.getName();
      File[] methodDirs = classDir.listFiles(File::isDirectory);
      if (methodDirs == null) continue;

      for (File methodDir : methodDirs) {
        String methodName = methodDir.getName();
        TestCase test = new TestCase(className, methodName);

        if (!session.getTestResults().containsKey(test)) {
          log.error(
              "No test result found for screenshot dir {}/{} — skipping", className, methodName);
          continue;
        }

        File destDir = new File(imageOutputDir, className + "/" + methodName);
        destDir.mkdirs();

        List<File> shots = moveScreenshots(methodDir, destDir);
        shots.forEach(f -> session.addScreenshot(test, f));
      }
    }
  }

  private List<File> moveScreenshots(File sourceDir, File destDir) throws IOException {
    File[] pngs = sourceDir.listFiles(f -> f.getName().endsWith(".png"));
    if (pngs == null) return Collections.emptyList();

    List<File> result = new ArrayList<>();
    Arrays.sort(pngs);
    for (File png : pngs) {
      File dest = new File(destDir, png.getName());
      FileUtils.moveFile(png, dest);
      result.add(dest);
    }
    return result;
  }
}
