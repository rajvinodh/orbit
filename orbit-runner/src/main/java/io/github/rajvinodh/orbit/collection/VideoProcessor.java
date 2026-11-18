package io.github.rajvinodh.orbit.collection;

import com.android.ddmlib.IDevice;
import io.github.rajvinodh.orbit.domain.TestCase;
import io.github.rajvinodh.orbit.domain.TestStatus;
import io.github.rajvinodh.orbit.orchestration.DeviceSession;
import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/**
 * Associates locally-recorded scrcpy video files with their test results. VideoRecorder already
 * deletes videos for passed tests, so only failed-test videos remain in the output directory when
 * this collector runs. Filename pattern: {className}-{methodName}-{timestamp}.mp4
 */
@Slf4j
public class VideoProcessor implements ArtifactCollector {

  static final Pattern FILENAME_PATTERN = Pattern.compile("^(.+)-(.+)-(\\d+)\\.mp4$");

  private final File videoOutputDir;

  public VideoProcessor(File videoOutputDir) {
    this.videoOutputDir = videoOutputDir;
  }

  @Override
  public void collect(IDevice device, DeviceSession session) throws IOException {
    if (!videoOutputDir.exists()) return;

    File[] mp4s = videoOutputDir.listFiles(f -> f.getName().endsWith(".mp4"));
    if (mp4s == null) return;

    for (File mp4 : mp4s) {
      TestCase test = parseTestCase(mp4.getName());
      if (test == null) {
        log.error("Malformed video filename, skipping: {}", mp4.getName());
        continue;
      }
      if (session.getTestResults().containsKey(test)
              && (session.getTestResults().get(test).getStatus() == TestStatus.FAIL)
          || session.getTestResults().get(test).getStatus() == TestStatus.ASSUMPTION_FAILURE) {
        session.addVideo(test, mp4);
      }
    }
  }

  // Package-private for testing
  static TestCase parseTestCase(String filename) {
    Matcher m = FILENAME_PATTERN.matcher(filename);
    if (!m.matches()) return null;
    return new TestCase(m.group(1), m.group(2));
  }
}
