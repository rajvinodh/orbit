package io.github.rajvinodh.orbit.report;

import java.io.File;
import lombok.Value;

@Value
public class ReportArtifact {
  File rootDir; // orbit-report/
  File indexFile; // orbit-report/index.html
}
