package io.github.rajvinodh.orbit.collection;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.jacoco.core.tools.ExecFileLoader;

@Slf4j
public class CoverageMerger {

  /**
   * Merges one coverage.ec per device serial into a single merged-coverage.ec. Skips devices whose
   * file is absent (e.g. all tests failed before any coverage was written).
   */
  public static void merge(Set<String> serials, File coverageOutputDir) throws IOException {
    ExecFileLoader loader = new ExecFileLoader();
    int loaded = 0;
    for (String serial : serials) {
      File ec = new File(coverageOutputDir, serial + "/coverage.ec");
      if (ec.exists()) {
        loader.load(ec);
        loaded++;
      } else {
        log.warn("No device-level coverage.ec for serial {}", serial);
      }
    }
    if (loaded == 0) {
      log.warn("No coverage files to merge");
      return;
    }
    File merged = new File(coverageOutputDir, "merged-coverage.ec");
    loader.save(merged, false);
    log.info(
        "Merged coverage from {}/{} device(s) → {}",
        loaded,
        serials.size(),
        merged.getAbsolutePath());
  }
}
