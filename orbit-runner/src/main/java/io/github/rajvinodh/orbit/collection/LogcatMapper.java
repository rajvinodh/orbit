package io.github.rajvinodh.orbit.collection;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.logcat.LogCatMessage;
import io.github.rajvinodh.orbit.domain.TestCase;
import io.github.rajvinodh.orbit.orchestration.DeviceSession;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LogcatMapper implements ArtifactCollector {

  @Override
  public void collect(IDevice device, DeviceSession session) {
    OrbitDeviceLogger logger = session.getLogcatLogger();
    if (logger == null) return;

    Map<TestCase, List<LogCatMessage>> parsed = logger.stop();
    parsed.forEach(session::addLogcat);
    log.debug("Logcat collected for {} test(s) on {}", parsed.size(), session.getSerial());
  }
}
