package io.github.rajvinodh.orbit.collection;

import com.android.ddmlib.DdmPreferences;
import com.android.ddmlib.SyncService;
import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;

/**
 * Progress monitor for ADB directory pulls via {@link SyncService}. Logs transfer progress at DEBUG
 * level and optionally cancels transfers that stall beyond a computed per-MB timeout.
 *
 * <p>Adapted from Spoon's {@code FileTransferSyncProgressMonitor}.
 */
@Slf4j
class FileTransferSyncProgressMonitor implements SyncService.ISyncProgressMonitor {

  private int totalFiles = 0;
  private Instant startTime;
  private long transferDeadlineMs = Long.MAX_VALUE;
  private boolean isCanceled = false;

  private final boolean detectHangs;
  private final Duration fileTransferTimeout;
  private final boolean customAdbTimeout;
  private final int previousAdbTimeout;

  FileTransferSyncProgressMonitor() {
    this.detectHangs = false;
    this.fileTransferTimeout = null;
    this.customAdbTimeout = false;
    this.previousAdbTimeout = 0;
  }

  FileTransferSyncProgressMonitor(
      Duration adbTimeout, boolean detectHangs, Duration fileTransferTimeout) {
    this.detectHangs = detectHangs;
    this.fileTransferTimeout = fileTransferTimeout;
    this.previousAdbTimeout = DdmPreferences.getTimeOut();
    this.customAdbTimeout = true;
    DdmPreferences.setTimeOut((int) adbTimeout.toMillis());
  }

  @Override
  public void start(int totalWork) {
    startTime = Instant.now();
    log.debug("Starting file transfer: {} bytes", totalWork);
    if (detectHangs && fileTransferTimeout != null) {
      double mb = (double) totalWork / 1_000_000;
      long timeoutMs = fileTransferTimeout.multipliedBy((long) mb + 1).toMillis();
      transferDeadlineMs = System.currentTimeMillis() + timeoutMs;
      log.debug("Hang detection enabled, timeout: {} ms", timeoutMs);
    }
  }

  @Override
  public void stop() {
    if (startTime != null) {
      Duration elapsed = Duration.between(startTime, Instant.now());
      int count = totalFiles == 0 ? 1 : totalFiles;
      if (elapsed.toSeconds() == 0) {
        log.debug("{} file(s) transferred in {} ms", count, elapsed.toMillis());
      } else {
        log.debug("{} file(s) transferred in {} s", count, elapsed.toSeconds());
      }
    }
    if (customAdbTimeout) {
      DdmPreferences.setTimeOut(previousAdbTimeout);
    }
  }

  @Override
  public boolean isCanceled() {
    return isCanceled;
  }

  @Override
  public void startSubTask(String name) {
    log.debug("Transferring: {}", name);
    totalFiles++;
  }

  @Override
  public void advance(int work) {
    if (work > 1024) {
      log.debug("Transferred: {} KB", work / 1000);
    }
    if (detectHangs && System.currentTimeMillis() > transferDeadlineMs) {
      log.warn("File transfer stall detected — cancelling");
      isCanceled = true;
    }
  }
}
