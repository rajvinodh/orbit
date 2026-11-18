package io.github.rajvinodh.orbit.client;

import android.graphics.Bitmap;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Static helpers for capturing screenshots and saving arbitrary files during a test. Requires
 * {@link OrbitRule} to be active on the test thread.
 *
 * <pre>{@code
 * @Rule public OrbitRule orbit = new OrbitRule(InstrumentationRegistry.getInstrumentation());
 *
 * @Test public void loginFlow() {
 *     // ... perform actions ...
 *     Orbit.screenshot("after_login");
 *     Orbit.addFile("response.json", responseFile);
 * }
 * }</pre>
 */
public final class Orbit {

  private static final String TAG = "Orbit";

  private Orbit() {}

  /**
   * Captures a full-device screenshot and saves it to {@code
   * /sdcard/app_orbit-screenshots/{class}/{method}/{tag}.png}. No-ops silently if {@link OrbitRule}
   * is not active or capture fails.
   *
   * @param tag logical name for this screenshot (used as the filename)
   */
  public static void screenshot(String tag) {
    OrbitRule.TestInfo info = OrbitRule.currentTest();
    if (info == null) {
      Log.w(TAG, "screenshot() called outside of OrbitRule — ignored");
      return;
    }

    File dir = screenshotDir(info);
    shellRun(info, "mkdir -p " + dir.getAbsolutePath());

    try {
      Bitmap bitmap = info.instrumentation.getUiAutomation().takeScreenshot();
      if (bitmap == null) {
        Log.w(TAG, "takeScreenshot() returned null for tag: " + tag);
        return;
      }

      // The app process cannot write directly to /sdcard/ on API 29+ without
      // WRITE_EXTERNAL_STORAGE. Write to the app's own external cache dir instead
      // (/sdcard/Android/data/<pkg>/cache/) — always writable by the app without
      // any permissions — then move to the final destination via shell.
      File extCache = info.instrumentation.getTargetContext().getExternalCacheDir();
      File tmp = new File(extCache, "orbit_" + sanitise(tag) + ".png");
      try {
        try (OutputStream os = new FileOutputStream(tmp)) {
          bitmap.compress(Bitmap.CompressFormat.PNG, 100, os);
        }
        bitmap.recycle();
        shellRun(
            info,
            "mv "
                + tmp.getAbsolutePath()
                + " "
                + new File(dir, sanitise(tag) + ".png").getAbsolutePath());
      } finally {
        tmp.delete(); // no-op if mv already moved it; cleans up if mv failed
      }
    } catch (Exception e) {
      Log.w(TAG, "screenshot failed for tag '" + tag + "': " + e.getMessage());
    }
  }

  /**
   * Copies {@code source} to {@code /sdcard/app_orbit-files/{class}/{method}/{name}}. The runner
   * pulls this file after the test and associates it with the test result.
   *
   * @param name filename to use in the report
   * @param source file to copy (must be readable at call time)
   */
  public static void addFile(String name, File source) {
    OrbitRule.TestInfo info = OrbitRule.currentTest();
    if (info == null) {
      Log.w(TAG, "addFile() called outside of OrbitRule — ignored");
      return;
    }
    if (!source.exists()) {
      Log.w(TAG, "addFile(): source does not exist: " + source);
      return;
    }

    File dir = fileDir(info);
    shellRun(info, "mkdir -p " + dir.getAbsolutePath());

    try {
      // Stage in external cache so the shell mv can reach both ends.
      File extCache = info.instrumentation.getTargetContext().getExternalCacheDir();
      File tmp = new File(extCache, "orbit_" + sanitise(name));
      try {
        Files.copy(source.toPath(), tmp.toPath(), StandardCopyOption.REPLACE_EXISTING);
        shellRun(
            info,
            "mv " + tmp.getAbsolutePath() + " " + new File(dir, sanitise(name)).getAbsolutePath());
      } finally {
        tmp.delete(); // no-op if mv already moved it; cleans up if mv failed
      }
    } catch (IOException e) {
      Log.w(TAG, "addFile failed for '" + name + "': " + e.getMessage());
    }
  }

  // ── internal helpers ──────────────────────────────────────────────────

  private static void shellRun(OrbitRule.TestInfo info, String command) {
    try (ParcelFileDescriptor pfd =
        info.instrumentation.getUiAutomation().executeShellCommand(command)) {
      // Drain stdout so the command fully completes before we return.
      try (InputStream in = new FileInputStream(pfd.getFileDescriptor())) {
        byte[] buf = new byte[256];
        while (in.read(buf) != -1) {
          /* drain */
        }
      }
    } catch (IOException e) {
      Log.w(TAG, "shell command failed [" + command + "]: " + e.getMessage());
    }
  }

  // ── path helpers (package-private for testing) ────────────────────────

  static File screenshotDir(OrbitRule.TestInfo info) {
    return new File("/sdcard/app_orbit-screenshots/" + info.className + "/" + info.methodName);
  }

  static File fileDir(OrbitRule.TestInfo info) {
    return new File("/sdcard/app_orbit-files/" + info.className + "/" + info.methodName);
  }

  static String sanitise(String name) {
    return name.replaceAll("[^a-zA-Z0-9._\\-]", "_");
  }
}
