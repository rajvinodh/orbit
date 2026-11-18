package io.github.rajvinodh.orbit.orchestration;

public class DeviceDisconnectedException extends RuntimeException {
  public DeviceDisconnectedException(String serial) {
    super("Device disconnected: " + serial);
  }

  public DeviceDisconnectedException(String serial, Throwable cause) {
    super("Device disconnected: " + serial, cause);
  }
}
