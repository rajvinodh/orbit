package io.github.rajvinodh.orbit.report;

import io.github.rajvinodh.orbit.domain.DeviceResult;
import io.github.rajvinodh.orbit.domain.OrbitSummary;
import io.github.rajvinodh.orbit.domain.TestResult;
import io.github.rajvinodh.orbit.domain.TestStatus;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import lombok.extern.slf4j.Slf4j;

/**
 * Writes one JUnit-compatible TEST-{serial}.xml per device into the report output directory. The
 * format is understood by GitHub Actions junit-reporter, Jenkins, and CircleCI.
 */
@Slf4j
public class JUnitXmlReportWriter {

  public void write(OrbitSummary summary, File outputDir) {
    File junitDir = new File(outputDir, "junit");
    junitDir.mkdirs();
    for (DeviceResult dr : summary.getResults().values()) {
      File xmlFile = new File(junitDir, "TEST-" + sanitize(dr.getSerial()) + ".xml");
      try (Writer w = new FileWriter(xmlFile)) {
        writeDevice(dr, w);
        log.info("JUnit XML written to {}", xmlFile.getName());
      } catch (IOException e) {
        log.error("Failed to write JUnit XML for {}: {}", dr.getSerial(), e.getMessage());
      }
    }
  }

  private void writeDevice(DeviceResult dr, Writer w) throws IOException {
    long failed =
        dr.getTestResults().values().stream().filter(t -> t.getStatus() == TestStatus.FAIL).count();
    long skipped =
        dr.getTestResults().values().stream()
            .filter(
                t ->
                    t.getStatus() == TestStatus.IGNORED
                        || t.getStatus() == TestStatus.ASSUMPTION_FAILURE)
            .count();

    w.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    w.write(
        String.format(
            "<testsuite name=\"%s\" tests=\"%d\" failures=\"%d\" errors=\"0\" skipped=\"%d\" time=\"%.3f\">\n",
            esc(dr.getSerial()),
            dr.getTestResults().size(),
            failed,
            skipped,
            dr.getDurationMs() / 1000.0));

    for (TestResult tr : dr.getTestResults().values()) {
      writeTestCase(tr, w);
    }

    w.write("</testsuite>\n");
  }

  private void writeTestCase(TestResult tr, Writer w) throws IOException {
    w.write(
        String.format(
            "  <testcase classname=\"%s\" name=\"%s\" time=\"%.3f\"",
            esc(tr.getTestCase().getClassName()),
            esc(tr.getTestCase().getMethodName()),
            tr.getDurationMs() / 1000.0));

    switch (tr.getStatus()) {
      case PASS -> w.write("/>\n");
      case FAIL -> {
        w.write(">\n");
        String ex = tr.getException() != null ? tr.getException() : "";
        String firstLine = ex.lines().findFirst().orElse("");
        String type =
            firstLine.contains(":")
                ? firstLine.substring(0, firstLine.indexOf(':')).trim()
                : "AssertionError";
        w.write(
            String.format(
                "    <failure message=\"%s\" type=\"%s\">%s</failure>\n",
                esc(firstLine), esc(type), esc(ex)));
        w.write("  </testcase>\n");
      }
      case IGNORED -> w.write(">\n    <skipped/>\n  </testcase>\n");
      case ASSUMPTION_FAILURE ->
          w.write(">\n    <skipped message=\"Assumption failed\"/>\n  </testcase>\n");
    }
  }

  private static String sanitize(String serial) {
    return serial.replaceAll("[^a-zA-Z0-9._-]", "_");
  }

  private static String esc(String s) {
    if (s == null) return "";
    return s.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }
}
