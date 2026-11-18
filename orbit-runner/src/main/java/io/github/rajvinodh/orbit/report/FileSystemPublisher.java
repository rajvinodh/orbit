package io.github.rajvinodh.orbit.report;

public class FileSystemPublisher implements ReportPublisher {
  @Override
  public void publish(ReportArtifact artifact) {
    // Files are already on disk — nothing to do.
  }
}
