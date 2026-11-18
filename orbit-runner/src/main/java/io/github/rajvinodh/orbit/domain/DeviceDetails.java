package io.github.rajvinodh.orbit.domain;

import lombok.Value;
import org.jetbrains.annotations.Nullable;

@Value
public class DeviceDetails {
  String model;
  @Nullable String osVersion;
  int apiLevel;
}
