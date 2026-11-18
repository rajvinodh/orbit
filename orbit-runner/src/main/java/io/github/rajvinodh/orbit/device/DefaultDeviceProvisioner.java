package io.github.rajvinodh.orbit.device;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.CollectingOutputReceiver;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.InstallException;
import io.github.rajvinodh.orbit.config.RunConfig;
import io.github.rajvinodh.orbit.domain.DeviceDetails;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

@Slf4j
public class DefaultDeviceProvisioner implements DeviceProvisioner {

  static final int MARSHMALLOW_API = 23;
  static final int ANDROID_11_API = 30;

  private static final List<String> DEVICE_DIRS =
      List.of("app_orbit-screenshots", "app_orbit-files", "app_orbit-videos");

  private final AndroidDebugBridge adb;
  private final DeviceConnectionChecker connectionChecker;

  public DefaultDeviceProvisioner(
      AndroidDebugBridge adb, DeviceConnectionChecker connectionChecker) {
    this.adb = adb;
    this.connectionChecker = connectionChecker;
  }

  @Override
  public ProvisionResult provision(IDevice device, RunConfig config) {
    if (!installApks(device, config)) return resolveFailure(device);
    if (!installAppiumSettings(device, config)) return resolveFailure(device);
    if (!cleanDeviceDirectories(device)) return resolveFailure(device);
    if (!grantPermissions(device, config)) return resolveFailure(device);
    if (!clearLogcat(device)) return resolveFailure(device);
    return ProvisionResult.SUCCESS;
  }

  /** Returns DISCONNECTED if the device is gone, otherwise PROVISIONING_FAILED. */
  private ProvisionResult resolveFailure(IDevice device) {
    return connectionChecker.isAvailable(adb, device.getSerialNumber())
        ? ProvisionResult.PROVISIONING_FAILED
        : ProvisionResult.DISCONNECTED;
  }

  private boolean installApks(IDevice device, RunConfig config) {
    String grantFlag = grantAllFlag(device, config);
    for (java.io.File apk : config.getOtherApks()) {
      if (!install(device, apk, grantFlag)) return false;
    }
    return install(device, config.getTestApk(), grantFlag);
  }

  private boolean install(IDevice device, java.io.File apk, String grantFlag) {
    try {
      device.installPackage(apk.getAbsolutePath(), true, grantFlag);
      return true;
    } catch (InstallException e) {
      log.error(
          "InstallException installing {} on {}: {}",
          apk.getName(),
          device.getSerialNumber(),
          e.getMessage());
      return false;
    }
  }

  /** Returns "-g" when grantAll is enabled and device API >= 23, otherwise empty string. */
  String grantAllFlag(IDevice device, RunConfig config) {
    if (!config.isGrantAll()) return "";
    int api = apiLevel(device);
    return api >= MARSHMALLOW_API ? "-g" : "";
  }

  private boolean cleanDeviceDirectories(IDevice device) {
    try {
      CollectingOutputReceiver receiver = new CollectingOutputReceiver();
      for (String dir : DEVICE_DIRS) {
        device.executeShellCommand("rm -rf /sdcard/" + dir, receiver, 60, TimeUnit.SECONDS);
      }
      return true;
    } catch (Exception e) {
      log.error(
          "Failed to clean device directories on {}: {}", device.getSerialNumber(), e.getMessage());
      return false;
    }
  }

  private boolean grantPermissions(IDevice device, RunConfig config) {
    try {
      int api = apiLevel(device);
      String pkg = config.getAppPackage();
      CollectingOutputReceiver receiver = new CollectingOutputReceiver();

      if (api >= MARSHMALLOW_API) {
        device.executeShellCommand(
            "pm grant " + pkg + " android.permission.READ_EXTERNAL_STORAGE",
            receiver,
            30,
            TimeUnit.SECONDS);
        device.executeShellCommand(
            "pm grant " + pkg + " android.permission.WRITE_EXTERNAL_STORAGE",
            receiver,
            30,
            TimeUnit.SECONDS);
      }
      if (api >= ANDROID_11_API) {
        device.executeShellCommand(
            "appops set --uid " + pkg + " MANAGE_EXTERNAL_STORAGE allow",
            receiver,
            30,
            TimeUnit.SECONDS);
      }
      return true;
    } catch (Exception e) {
      log.error("Failed to grant permissions on {}: {}", device.getSerialNumber(), e.getMessage());
      return false;
    }
  }

  private boolean clearLogcat(IDevice device) {
    try {
      device.executeShellCommand("logcat -c", new CollectingOutputReceiver(), 30, TimeUnit.SECONDS);
      return true;
    } catch (Exception e) {
      log.error("Failed to clear logcat on {}: {}", device.getSerialNumber(), e.getMessage());
      return false;
    }
  }

  private int apiLevel(IDevice device) {
    String api = device.getProperty("ro.build.version.sdk");
    try {
      return api != null ? Integer.parseInt(api.trim()) : 0;
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  // ── Appium Settings provisioning ──────────────────────────────────────

  private static final String APPIUM_PKG = "io.appium.settings";

  private boolean installAppiumSettings(IDevice device, RunConfig config) {
    String required = config.getAppiumSettingsVersion();
    if (required == null) return true;

    try {
      String installed = appiumSettingsVersion(device);
      if (installed != null && compareVersions(installed, required) >= 0) {
        log.info(
            "io.appium.settings v{} already installed on {}", installed, device.getSerialNumber());
        return true;
      }
      if (installed != null) {
        CollectingOutputReceiver r = new CollectingOutputReceiver();
        device.uninstallPackage(APPIUM_PKG);
        log.info("Uninstalled io.appium.settings {} on {}", installed, device.getSerialNumber());
      }
      File apk = downloadAppiumSettings(required);
      device.installPackage(apk.getAbsolutePath(), true);
      log.info("Installed io.appium.settings v{} on {}", required, device.getSerialNumber());
      return true;
    } catch (Exception e) {
      log.error(
          "Failed to install io.appium.settings on {}: {}",
          device.getSerialNumber(),
          e.getMessage());
      return false;
    }
  }

  private String appiumSettingsVersion(IDevice device) throws Exception {
    CollectingOutputReceiver r = new CollectingOutputReceiver();
    device.executeShellCommand("dumpsys package " + APPIUM_PKG, r, 10, TimeUnit.SECONDS);
    for (String line : r.getOutput().split("\n")) {
      String trimmed = line.trim();
      if (trimmed.startsWith("versionName=")) {
        return trimmed.substring("versionName=".length()).trim();
      }
    }
    return null;
  }

  private File downloadAppiumSettings(String version) throws IOException {
    String url =
        "https://github.com/appium/io.appium.settings/releases/download/v"
            + version
            + "/settings_apk-debug.apk";
    File tmp = File.createTempFile("settings_apk-debug", ".apk");
    tmp.deleteOnExit();
    log.info("Downloading io.appium.settings v{} …", version);
    FileUtils.copyURLToFile(new URL(url), tmp, 30_000, 60_000);
    return tmp;
  }

  static int compareVersions(String a, String b) {
    String[] pa = a.split("\\.");
    String[] pb = b.split("\\.");
    int len = Math.max(pa.length, pb.length);
    for (int i = 0; i < len; i++) {
      int va = i < pa.length ? parseVersionSegment(pa[i]) : 0;
      int vb = i < pb.length ? parseVersionSegment(pb[i]) : 0;
      if (va != vb) return Integer.compare(va, vb);
    }
    return 0;
  }

  private static int parseVersionSegment(String s) {
    try {
      return Integer.parseInt(s.trim());
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  /** Reads basic device info — used by DeviceOrchestrator after provisioning. */
  public static DeviceDetails readDeviceDetails(IDevice device) {
    String model = device.getProperty("ro.product.model");
    String osVersion = device.getProperty("ro.build.version.release");
    String apiStr = device.getProperty("ro.build.version.sdk");
    int api = 0;
    try {
      if (apiStr != null) api = Integer.parseInt(apiStr.trim());
    } catch (NumberFormatException ignored) {
    }
    return new DeviceDetails(model != null ? model : "Unknown", osVersion, api);
  }
}
