package io.github.rajvinodh.orbit.domain;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.jetbrains.annotations.Nullable;

@Value
@Builder(toBuilder = true)
public class DeviceResult {
  String serial;
  DeviceStatus status;
  @Nullable DeviceDetails deviceDetails;
  @Nullable String provisioningFailureMessage;
  long startedAt;
  long durationMs;
  @Singular Map<TestCase, TestResult> testResults;
  @Singular List<String> exceptions;
  @Nullable DeviceDisconnectionInfo disconnection;

  public boolean isDisconnected() {
    return status == DeviceStatus.DISCONNECTED;
  }

  public boolean isProvisioningFailed() {
    return status == DeviceStatus.PROVISIONING_FAILED;
  }
}
