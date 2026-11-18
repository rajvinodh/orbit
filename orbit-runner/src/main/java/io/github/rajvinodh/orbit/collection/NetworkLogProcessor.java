package io.github.rajvinodh.orbit.collection;

import com.android.ddmlib.IDevice;
import io.github.rajvinodh.orbit.domain.TestCase;
import io.github.rajvinodh.orbit.orchestration.DeviceSession;
import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/**
 * Maps Chucker DB files already present in orbit-report/network/{serial}/ to their TestResults.
 * Files were pulled by NetworkLogPuller during execution (Phase 3). Filename pattern:
 * {className}-{methodName}-{timestamp}-chucker.db
 */
@Slf4j
public class NetworkLogProcessor implements ArtifactCollector {

  static final Pattern FILENAME_PATTERN = Pattern.compile("^(.+)-(.+)-(\\d+)-chucker\\.db$");

  private final File networkDir; // orbit-report/network/{serial}/

  public NetworkLogProcessor(File networkDir) {
    this.networkDir = networkDir;
  }

  @Override
  public void collect(IDevice device, DeviceSession session) throws IOException {
    if (!networkDir.exists()) return;

    File[] dbs = networkDir.listFiles(f -> f.getName().endsWith("-chucker.db"));
    if (dbs == null) return;

    for (File db : dbs) {
      TestCase test = parseTestCase(db.getName());
      if (test == null) {
        log.error("Unmatched network log filename, skipping: {}", db.getName());
        continue;
      }
      if (session.getTestResults().containsKey(test)) {
        session.addFile(test, db); // reuse files slot for DB association
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
