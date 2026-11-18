package io.github.rajvinodh.orbit.collection;

import com.android.ddmlib.IDevice;
import io.github.rajvinodh.orbit.domain.TestCase;
import io.github.rajvinodh.orbit.orchestration.DeviceSession;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

/**
 * Pulls app_orbit-files/ from device to orbit-report/files/{serial}/{class}/{method}/ and
 * associates each file with its TestResult.
 */
@Slf4j
public class FileProcessor implements ArtifactCollector {

  private static final String DEVICE_FILE_DIR = "/sdcard/app_orbit-files";

  private final File fileOutputDir; // orbit-report/files/{serial}/

  public FileProcessor(File fileOutputDir) {
    this.fileOutputDir = fileOutputDir;
  }

  @Override
  public void collect(IDevice device, DeviceSession session) throws IOException {
    File localPullDir = new File(fileOutputDir, ".pull-tmp");
    pullFromDevice(device, localPullDir);
    processLocalDir(localPullDir, session);
    FileUtils.deleteQuietly(localPullDir);
  }

  private void pullFromDevice(IDevice device, File dest) {
    DevicePuller.pullDirectory(device, DEVICE_FILE_DIR, dest);
  }

  private void processLocalDir(File pullDir, DeviceSession session) throws IOException {
    File fileRoot = new File(pullDir, "app_orbit-files");
    if (!fileRoot.exists()) return;

    File[] classDirs = fileRoot.listFiles(File::isDirectory);
    if (classDirs == null) return;

    for (File classDir : classDirs) {
      String className = classDir.getName();
      File[] methodDirs = classDir.listFiles(File::isDirectory);
      if (methodDirs == null) continue;

      for (File methodDir : methodDirs) {
        String methodName = methodDir.getName();
        TestCase test = new TestCase(className, methodName);

        if (!session.getTestResults().containsKey(test)) {
          log.error("No test result for file dir {}/{} — skipping", className, methodName);
          continue;
        }

        File destDir = new File(fileOutputDir, className + "/" + methodName);
        FileUtils.copyDirectory(methodDir, destDir);

        Collection<File> copied = FileUtils.listFiles(destDir, null, true);
        List<File> sorted = new ArrayList<>(copied);
        sorted.sort(File::compareTo);
        sorted.forEach(f -> session.addFile(test, f));
      }
    }
  }
}
