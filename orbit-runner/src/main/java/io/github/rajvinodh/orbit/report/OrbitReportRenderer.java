package io.github.rajvinodh.orbit.report;

import com.android.ddmlib.logcat.LogCatHeader;
import com.android.ddmlib.logcat.LogCatMessage;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import io.github.rajvinodh.orbit.domain.DeviceDetails;
import io.github.rajvinodh.orbit.domain.DeviceResult;
import io.github.rajvinodh.orbit.domain.ExecutionMode;
import io.github.rajvinodh.orbit.domain.OrbitSummary;
import io.github.rajvinodh.orbit.domain.TestCase;
import io.github.rajvinodh.orbit.domain.TestResult;
import io.github.rajvinodh.orbit.domain.TestStatus;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

@Slf4j
public class OrbitReportRenderer implements ReportRenderer {

  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
  private static final DateTimeFormatter TITLE_DATE_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

  @Override
  public ReportArtifact render(OrbitSummary summary, File outputDir) throws IOException {
    outputDir.mkdirs();

    JsonObject reportJson = buildReport(summary, outputDir);
    writeDataJson(reportJson, outputDir);
    File indexFile = writeIndexHtml(reportJson, outputDir);

    log.info("Report written to {}", outputDir.getAbsolutePath());
    return new ReportArtifact(outputDir, indexFile);
  }

  // ── data.json ─────────────────────────────────────────────────────────

  private JsonObject buildReport(OrbitSummary summary, File outputDir) {
    JsonObject root = new JsonObject();
    root.add("meta", buildMeta(summary));
    root.add("summary", buildSummary(summary));
    root.add("devices", buildDevices(summary, outputDir));
    if (summary.getExecutionMode() == ExecutionMode.FANOUT) {
      root.add("tests", buildFanoutTests(summary));
    } else {
      root.add("tests", JsonNull.INSTANCE);
    }
    return root;
  }

  private void writeDataJson(JsonObject reportJson, File outputDir) throws IOException {
    try (Writer w = new FileWriter(new File(outputDir, "data.json"), StandardCharsets.UTF_8)) {
      GSON.toJson(reportJson, w);
    }
  }

  private JsonObject buildMeta(OrbitSummary summary) {
    JsonObject meta = new JsonObject();
    meta.addProperty("title", resolveTitle(summary));
    meta.addProperty("startedAt", summary.getStartedAt());
    meta.addProperty("durationMs", summary.getDurationMs());
    meta.addProperty("orbitVersion", summary.getOrbitVersion());
    meta.addProperty("executionMode", summary.getExecutionMode().name());
    return meta;
  }

  private JsonObject buildSummary(OrbitSummary summary) {
    if (summary.getExecutionMode() == ExecutionMode.FANOUT) {
      return buildFanoutSummary(summary);
    }
    return buildDistributeSummary(summary);
  }

  private JsonObject buildDistributeSummary(OrbitSummary summary) {
    int total = summary.getMissedTests().size();
    int passed = 0, failed = 0, ignored = summary.getMissedTests().size();

    for (DeviceResult dr : summary.getResults().values()) {
      for (TestResult tr : dr.getTestResults().values()) {
        total++;
        switch (tr.getStatus()) {
          case PASS -> passed++;
          case FAIL -> failed++;
          case IGNORED, ASSUMPTION_FAILURE -> ignored++;
        }
      }
    }

    JsonObject s = new JsonObject();
    s.addProperty("total", total);
    s.addProperty("passed", passed);
    s.addProperty("failed", failed);
    s.addProperty("ignored", ignored);
    return s;
  }

  private JsonObject buildFanoutSummary(OrbitSummary summary) {
    Map<TestCase, TestStatus> aggregated = aggregateFanout(summary);

    int total = summary.getMissedTests().size() + aggregated.size();
    int passed = 0, failed = 0, ignored = summary.getMissedTests().size();

    for (TestStatus status : aggregated.values()) {
      switch (status) {
        case PASS -> passed++;
        case FAIL -> failed++;
        case IGNORED, ASSUMPTION_FAILURE -> ignored++;
      }
    }

    JsonObject s = new JsonObject();
    s.addProperty("total", total);
    s.addProperty("passed", passed);
    s.addProperty("failed", failed);
    s.addProperty("ignored", ignored);
    return s;
  }

  private Map<TestCase, TestStatus> aggregateFanout(OrbitSummary summary) {
    Map<TestCase, List<TestStatus>> statusesByTest = new LinkedHashMap<>();
    for (DeviceResult dr : summary.getResults().values()) {
      for (Map.Entry<TestCase, TestResult> e : dr.getTestResults().entrySet()) {
        statusesByTest
            .computeIfAbsent(e.getKey(), k -> new ArrayList<>())
            .add(e.getValue().getStatus());
      }
    }
    Map<TestCase, TestStatus> result = new LinkedHashMap<>();
    for (Map.Entry<TestCase, List<TestStatus>> e : statusesByTest.entrySet()) {
      result.put(e.getKey(), worstWins(e.getValue()));
    }
    return result;
  }

  private TestStatus worstWins(List<TestStatus> statuses) {
    if (statuses.contains(TestStatus.FAIL)) return TestStatus.FAIL;
    if (statuses.contains(TestStatus.ASSUMPTION_FAILURE)) return TestStatus.ASSUMPTION_FAILURE;
    if (statuses.contains(TestStatus.IGNORED)) return TestStatus.IGNORED;
    return TestStatus.PASS;
  }

  private JsonArray buildFanoutTests(OrbitSummary summary) {
    Map<TestCase, TestStatus> aggregated = aggregateFanout(summary);
    JsonArray arr = new JsonArray();
    for (Map.Entry<TestCase, TestStatus> e : aggregated.entrySet()) {
      TestCase tc = e.getKey();
      JsonObject obj = new JsonObject();
      obj.addProperty("className", tc.getClassName());
      obj.addProperty("methodName", tc.getMethodName());
      obj.addProperty("aggregatedStatus", e.getValue().name());

      JsonObject deviceStatuses = new JsonObject();
      for (DeviceResult dr : summary.getResults().values()) {
        TestResult tr = dr.getTestResults().get(tc);
        if (tr != null) {
          deviceStatuses.addProperty(dr.getSerial(), tr.getStatus().name());
        }
      }
      obj.add("deviceStatuses", deviceStatuses);
      arr.add(obj);
    }
    return arr;
  }

  private JsonArray buildDevices(OrbitSummary summary, File outputDir) {
    JsonArray arr = new JsonArray();
    for (DeviceResult dr : summary.getResults().values()) {
      arr.add(buildDevice(dr, outputDir));
    }
    return arr;
  }

  private JsonObject buildDevice(DeviceResult dr, File outputDir) {
    JsonObject d = new JsonObject();
    d.addProperty("serial", dr.getSerial());
    d.addProperty("status", dr.getStatus().name());
    d.addProperty("durationMs", dr.getDurationMs());

    DeviceDetails details = dr.getDeviceDetails();
    if (details != null) {
      d.addProperty("model", details.getModel());
      d.addProperty("apiLevel", details.getApiLevel());
      d.addProperty("osVersion", details.getOsVersion());
    } else {
      d.add("model", JsonNull.INSTANCE);
      d.add("apiLevel", JsonNull.INSTANCE);
      d.add("osVersion", JsonNull.INSTANCE);
    }

    if (dr.getDisconnection() != null) {
      d.addProperty("disconnectedAt", dr.getDisconnection().getDiscoveredAtMs());
    } else {
      d.add("disconnectedAt", JsonNull.INSTANCE);
    }

    JsonArray tests = new JsonArray();
    for (TestResult tr : dr.getTestResults().values()) {
      tests.add(buildTest(tr, outputDir.toPath()));
    }
    d.add("tests", tests);

    return d;
  }

  private JsonObject buildTest(TestResult tr, Path reportRoot) {
    JsonObject t = new JsonObject();
    t.addProperty("className", tr.getTestCase().getClassName());
    t.addProperty("methodName", tr.getTestCase().getMethodName());
    t.addProperty("status", tr.getStatus().name());
    t.addProperty("startedAt", tr.getStartedAt());
    t.addProperty("durationMs", tr.getDurationMs());
    addNullable(t, "externalId", tr.getExternalId());
    addNullable(t, "exception", tr.getException());
    addNullable(t, "ignoredReason", tr.getIgnoredReason());

    JsonArray shots = new JsonArray();
    tr.getScreenshots().forEach(f -> shots.add(relativize(reportRoot, f)));
    t.add("screenshots", shots);

    if (tr.getVideos().isEmpty()) {
      t.add("video", JsonNull.INSTANCE);
    } else {
      t.addProperty("video", relativize(reportRoot, tr.getVideos().get(0)));
    }

    JsonArray files = new JsonArray();
    tr.getFiles().forEach(f -> files.add(relativize(reportRoot, f)));
    t.add("files", files);

    t.add("logcat", buildLogcat(tr.getLogcat()));
    t.add("crashLog", buildLogcat(tr.getCrashLog()));

    return t;
  }

  private JsonArray buildLogcat(List<LogCatMessage> messages) {
    JsonArray arr = new JsonArray();
    for (LogCatMessage msg : messages) {
      LogCatHeader h = msg.getHeader();
      JsonObject entry = new JsonObject();
      entry.addProperty("time", h.getTimestamp().toString());
      entry.addProperty("level", String.valueOf(h.getLogLevel().getPriorityLetter()));
      entry.addProperty("tag", h.getTag());
      entry.addProperty("pid", h.getPid());
      entry.addProperty("msg", msg.getMessage());
      arr.add(entry);
    }
    return arr;
  }

  // ── index.html ────────────────────────────────────────────────────────

  private File writeIndexHtml(JsonObject reportJson, File outputDir) throws IOException {
    File indexFile = new File(outputDir, "index.html");
    try (InputStream in = getClass().getResourceAsStream("/orbit-report/index.html")) {
      if (in != null) {
        // Inject data inline so the report works when opened via file:// without a server.
        String inlineData =
            "<script>window.__ORBIT_DATA__=" + GSON.toJson(reportJson) + ";</script>";
        String html =
            new String(in.readAllBytes(), StandardCharsets.UTF_8)
                .replace("</head>", inlineData + "</head>");
        FileUtils.writeStringToFile(indexFile, html, StandardCharsets.UTF_8);
        copyStaticAssets(outputDir);
      } else {
        writeFallbackIndexHtml(indexFile);
        log.warn("No bundled React index.html found — wrote minimal fallback report.");
      }
    }
    return indexFile;
  }

  private void copyStaticAssets(File outputDir) throws IOException {
    for (String asset : List.of("main.js")) {
      try (InputStream in = getClass().getResourceAsStream("/orbit-report/" + asset)) {
        if (in != null) {
          FileUtils.copyInputStreamToFile(in, new File(outputDir, asset));
        }
      }
    }
  }

  private void writeFallbackIndexHtml(File indexFile) throws IOException {
    String html =
        """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>Orbit Test Report</title>
                  <style>
                    *{box-sizing:border-box;margin:0;padding:0}
                    body{font-family:system-ui,sans-serif;background:#0f172a;color:#e2e8f0;padding:2rem}
                    h1{font-size:1.5rem;margin-bottom:1rem;color:#f1f5f9}
                    .card{background:#1e293b;border-radius:.5rem;padding:1rem;margin-bottom:1rem}
                    .badge{display:inline-block;padding:.15rem .5rem;border-radius:.25rem;font-size:.75rem;font-weight:600}
                    .pass{background:#166534;color:#bbf7d0}.fail{background:#7f1d1d;color:#fecaca}
                    .ignored{background:#374151;color:#d1d5db}
                    table{width:100%;border-collapse:collapse;margin-top:.5rem}
                    td,th{padding:.4rem .6rem;text-align:left;border-bottom:1px solid #334155;font-size:.85rem}
                    pre{background:#0f172a;padding:1rem;border-radius:.25rem;overflow-x:auto;font-size:.75rem;margin-top:.5rem}
                    summary{cursor:pointer;padding:.4rem;color:#94a3b8}
                  </style>
                </head>
                <body>
                  <div id="root"><p style="color:#94a3b8">Loading report…</p></div>
                  <script>
                  fetch('data.json').then(r=>r.json()).then(render);
                  function render(d){
                    const root=document.getElementById('root');
                    const m=d.meta, s=d.summary;
                    root.innerHTML=`
                      <h1>${m.title}</h1>
                      <div class="card" style="display:flex;gap:2rem;margin-bottom:1.5rem">
                        <div><div style="font-size:2rem;font-weight:700">${s.total}</div><div>Total</div></div>
                        <div><div style="font-size:2rem;font-weight:700;color:#4ade80">${s.passed}</div><div>Passed</div></div>
                        <div><div style="font-size:2rem;font-weight:700;color:#f87171">${s.failed}</div><div>Failed</div></div>
                        <div><div style="font-size:2rem;font-weight:700;color:#94a3b8">${s.ignored}</div><div>Ignored</div></div>
                      </div>
                      ${d.devices.map(deviceHtml).join('')}`;
                  }
                  function statusBadge(st){return`<span class="badge ${st.toLowerCase()}">${st}</span>`}
                  function deviceHtml(dev){
                    return`<div class="card">
                      <h2 style="margin-bottom:.5rem">${dev.serial} — ${statusBadge(dev.status)}</h2>
                      <div style="font-size:.8rem;color:#94a3b8">${dev.model??''} API ${dev.apiLevel??''} · ${(dev.durationMs/1000).toFixed(1)}s</div>
                      <table><thead><tr><th>Test</th><th>Status</th><th>Duration</th><th>Details</th></tr></thead>
                      <tbody>${dev.tests.map(testRow).join('')}</tbody></table></div>`;
                  }
                  function testRow(t){
                    const details=t.exception?`<details><summary>Stack trace</summary><pre>${escHtml(t.exception)}</pre></details>`:'';
                    return`<tr>
                      <td>${t.className}<br><strong>${t.methodName}</strong></td>
                      <td>${statusBadge(t.status)}</td>
                      <td>${(t.durationMs/1000).toFixed(2)}s</td>
                      <td>${details}</td></tr>`;
                  }
                  function escHtml(s){return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;')}
                  </script>
                </body>
                </html>
                """;
    FileUtils.writeStringToFile(indexFile, html, StandardCharsets.UTF_8);
  }

  // ── helpers ───────────────────────────────────────────────────────────

  private String resolveTitle(OrbitSummary summary) {
    if (summary.getTitle() != null) return summary.getTitle();
    return "Orbit Report · " + TITLE_DATE_FMT.format(Instant.ofEpochMilli(summary.getStartedAt()));
  }

  private String relativize(Path root, File file) {
    try {
      return root.relativize(file.toPath()).toString().replace(File.separatorChar, '/');
    } catch (IllegalArgumentException e) {
      return file.getAbsolutePath();
    }
  }

  private void addNullable(JsonObject obj, String key, String value) {
    if (value != null) obj.addProperty(key, value);
    else obj.add(key, JsonNull.INSTANCE);
  }
}
