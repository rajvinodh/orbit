package io.github.rajvinodh.orbit.device;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;

/** Single responsibility: check whether a serial is still ONLINE. Extracted for testability. */
public class DeviceConnectionChecker {

  public boolean isAvailable(AndroidDebugBridge adb, String serial) {
    for (IDevice device : adb.getDevices()) {
      if (device.getSerialNumber().equals(serial)) {
        return device.getState() == IDevice.DeviceState.ONLINE;
      }
    }
    return false;
  }
}
