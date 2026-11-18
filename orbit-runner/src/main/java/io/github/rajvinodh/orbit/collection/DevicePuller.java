package io.github.rajvinodh.orbit.collection;

import com.android.ddmlib.FileListingService;
import com.android.ddmlib.FileListingService.FileEntry;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.SyncService;
import java.io.File;
import java.lang.reflect.Constructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Shared helper for pulling directories from a device via SyncService.
 *
 * <p>{@code device.pullFile()} only handles single files; pulling a remote directory requires
 * {@link SyncService#pull(FileEntry[], String, SyncService.ISyncProgressMonitor)}, which takes
 * {@link FileEntry} objects. Because {@code FileEntry} has a package-private constructor, we build
 * them segment-by-segment via reflection (same approach as Spoon's {@code
 * SpoonUtils.obtainDirectoryFileEntry}).
 */
@Slf4j
final class DevicePuller {

  private DevicePuller() {}

  /**
   * Pulls {@code remotePath} (a directory) from {@code device} into {@code localDest}. Logs a
   * warning and returns without throwing if anything goes wrong.
   */
  static void pullDirectory(IDevice device, String remotePath, File localDest) {
    localDest.mkdirs();
    FileEntry remoteEntry = toDirectoryFileEntry(remotePath);
    if (remoteEntry == null) {
      log.warn(
          "Could not build FileEntry for {} — skipping pull from {}",
          remotePath,
          device.getSerialNumber());
      return;
    }
    SyncService sync = null;
    try {
      sync = device.getSyncService();
      if (sync == null) {
        log.warn("Could not get SyncService for {}", device.getSerialNumber());
        return;
      }
      sync.pull(
          new FileEntry[] {remoteEntry},
          localDest.getAbsolutePath(),
          new FileTransferSyncProgressMonitor());
    } catch (Exception e) {
      log.warn(
          "Could not pull {} from {}: {}", remotePath, device.getSerialNumber(), e.getMessage());
    } finally {
      if (sync != null) sync.close();
    }
  }

  // FileEntry's constructor is package-private in ddmlib; build the entry path
  // segment-by-segment via reflection.
  private static FileEntry toDirectoryFileEntry(String path) {
    try {
      Constructor<FileEntry> ctor =
          FileEntry.class.getDeclaredConstructor(
              FileEntry.class, String.class, int.class, boolean.class);
      ctor.setAccessible(true);
      FileEntry entry = null;
      for (String segment : path.split("/")) {
        entry = ctor.newInstance(entry, segment, FileListingService.TYPE_DIRECTORY, entry == null);
      }
      return entry;
    } catch (Exception e) {
      log.warn("Could not construct FileEntry for {}: {}", path, e.getMessage());
      return null;
    }
  }
}
