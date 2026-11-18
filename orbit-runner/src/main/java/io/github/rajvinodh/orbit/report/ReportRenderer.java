package io.github.rajvinodh.orbit.report;

import io.github.rajvinodh.orbit.domain.OrbitSummary;
import java.io.File;
import java.io.IOException;

public interface ReportRenderer {
  ReportArtifact render(OrbitSummary summary, File outputDir) throws IOException;
}
