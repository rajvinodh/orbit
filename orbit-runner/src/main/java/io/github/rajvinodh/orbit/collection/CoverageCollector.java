package io.github.rajvinodh.orbit.collection;

import com.android.ddmlib.IDevice;
import io.github.rajvinodh.orbit.orchestration.DeviceSession;
import java.io.File;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.jacoco.core.tools.ExecFileLoader;

/**
 * Phase 4 collector: merges the per-test coverage.ec files pulled by CoveragePuller (a
 * PostTestAction) into a single device-level coverage.ec file. CoverageMerger then aggregates
 * device-level files across all devices after Phase 4.
 */
@Slf4j
public class CoverageCollector implements ArtifactCollector {

  private final File coverageOutputDir;

  public CoverageCollector(File coverageOutputDir) {
    this.coverageOutputDir = coverageOutputDir;
  }

  @Override
  public void collect(IDevice device, DeviceSession session) throws IOException {

    File serialDir = new File(coverageOutputDir, device.getSerialNumber());
    File[] ecFiles = serialDir.listFiles((d, n) -> n.endsWith(".ec") && !n.equals("coverage.ec"));
    if (ecFiles == null || ecFiles.length == 0) {
      log.warn("No per-test coverage files found in {}", serialDir);
      return;
    }

    ExecFileLoader loader = new ExecFileLoader();
    for (File ec : ecFiles) {
      loader.load(ec);
      log.debug("Loaded {}", ec.getName());
    }
    File merged = new File(serialDir, "coverage.ec");
    loader.save(merged, false);
    log.info("Merged {} test coverage file(s) → {}", ecFiles.length, merged.getName());
  }
}
