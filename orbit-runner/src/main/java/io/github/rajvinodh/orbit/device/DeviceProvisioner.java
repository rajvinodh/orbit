package io.github.rajvinodh.orbit.device;

import com.android.ddmlib.IDevice;
import io.github.rajvinodh.orbit.config.RunConfig;

public interface DeviceProvisioner {
  ProvisionResult provision(IDevice device, RunConfig config);
}
