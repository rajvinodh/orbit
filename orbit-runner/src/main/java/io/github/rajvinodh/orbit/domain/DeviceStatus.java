package io.github.rajvinodh.orbit.domain;

public enum DeviceStatus {
  /** APK install, permission grant, or directory clean failed. Device still reachable. */
  PROVISIONING_FAILED,

  /** ADB availability lost at any stage — provisioning or execution. */
  DISCONNECTED,

  /** Device ran tests to completion. Individual tests may still have failed. */
  COMPLETED
}
