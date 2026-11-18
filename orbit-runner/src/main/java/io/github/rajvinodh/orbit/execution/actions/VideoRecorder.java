package io.github.rajvinodh.orbit.execution.actions;

import com.android.ddmlib.IDevice;
import io.github.rajvinodh.orbit.domain.TestCase;
import io.github.rajvinodh.orbit.execution.TestExecutionContext;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

@Slf4j
public class VideoRecorder implements PreTestAction, PostTestAction {

  private record ActiveRecording(Process process, File outputFile, CountDownLatch complete) {}

  private final File outputDir;
  private final ConcurrentHashMap<String, ActiveRecording> active = new ConcurrentHashMap<>();

  public VideoRecorder(File outputDir) {
    this.outputDir = outputDir;
  }

  @Override
  public void before(IDevice device, TestExecutionContext ctx) throws Exception {
    outputDir.mkdirs();
    File outputFile = new File(outputDir, buildFilename(ctx.getTestCase()));
    Process process = startProcess(ctx.getDeviceSerial(), outputFile);
    CountDownLatch complete = drainAndWaitForStart(process);
    active.put(ctx.getDeviceSerial(), new ActiveRecording(process, outputFile, complete));
    log.debug(
        "scrcpy recording started for {} → {}",
        ctx.getTestCase().displayName(),
        outputFile.getName());
  }

  @Override
  public void after(IDevice device, TestExecutionContext ctx, boolean testFailed) throws Exception {
    ActiveRecording rec = active.remove(ctx.getDeviceSerial());
    if (rec == null) {
      log.warn("No active recording found for device {}", ctx.getDeviceSerial());
      return;
    }

    List<ProcessHandle> descendants = rec.process().toHandle().descendants().toList();
    descendants.forEach(
        child ->
            log.debug(
                "Stopping scrcpy child process: pid={} cmd={}",
                child.pid(),
                child.info().command().orElse("unknown")));

    for (ProcessHandle child : descendants) {
      new ProcessBuilder("kill", "-2", String.valueOf(child.pid())).start().waitFor();
    }
    new ProcessBuilder("kill", "-2", String.valueOf(rec.process().pid())).start().waitFor();

    try {
      if (!rec.complete().await(30, TimeUnit.SECONDS)) {
        log.warn("scrcpy did not finalize recording within 30s — file may be corrupted");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    descendants.forEach(ProcessHandle::destroyForcibly);
    rec.process().destroyForcibly();

    log.debug("scrcpy recording stopped for {}", ctx.getTestCase().displayName());
    if (!testFailed) {
      FileUtils.deleteQuietly(rec.outputFile());
    }
  }

  // Package-private for testing
  Process startProcess(String serial, File outputFile) throws IOException {
    return new ProcessBuilder(
            List.of(
                "scrcpy",
                "--serial",
                serial,
                "--record",
                outputFile.getAbsolutePath(),
                "--no-playback"))
        .redirectErrorStream(true)
        .start();
  }

  // redirectErrorStream(true) merges stderr into stdout so one reader covers both.
  // [FFmpeg] codec-init warning fires before "Recording started", so it acts as the
  // primary start signal. The same thread watches for "Recording complete" so after()
  // knows the moov atom is written before cleaning up the process.
  private CountDownLatch drainAndWaitForStart(Process process) throws InterruptedException {
    CountDownLatch started = new CountDownLatch(1);
    CountDownLatch complete = new CountDownLatch(1);

    Thread reader =
        new Thread(
            () -> {
              try (BufferedReader br =
                  new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                  log.debug("scrcpy: {}", line);
                  if (line.contains("Recording started") || line.contains("[FFmpeg]")) {
                    started.countDown();
                  }
                  if (line.contains("Recording complete")) {
                    complete.countDown();
                  }
                }
              } catch (IOException ignored) {
              }
            },
            "scrcpy-stdout");

    reader.setDaemon(true);
    reader.start();

    if (!started.await(15, TimeUnit.SECONDS)) {
      log.warn("Timed out waiting for scrcpy recording to start");
    }
    return complete;
  }

  static String buildFilename(TestCase test) {
    return test.getClassName()
        + "-"
        + test.getMethodName()
        + "-"
        + System.currentTimeMillis()
        + ".mp4";
  }
}
