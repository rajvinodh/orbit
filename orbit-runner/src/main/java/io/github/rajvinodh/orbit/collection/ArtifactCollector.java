package io.github.rajvinodh.orbit.collection;

import com.android.ddmlib.IDevice;
import io.github.rajvinodh.orbit.orchestration.DeviceSession;
import java.io.IOException;

public interface ArtifactCollector {
  void collect(IDevice device, DeviceSession session) throws IOException;
}
