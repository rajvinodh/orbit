package io.github.rajvinodh.orbit.report;

@FunctionalInterface
public interface ReportPublisher {
  void publish(ReportArtifact artifact);
}
