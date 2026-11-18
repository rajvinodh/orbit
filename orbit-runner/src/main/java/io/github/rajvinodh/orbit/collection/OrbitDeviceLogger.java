package io.github.rajvinodh.orbit.collection;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.logcat.LogCatListener;
import com.android.ddmlib.logcat.LogCatMessage;
import com.android.ddmlib.logcat.LogCatReceiverTask;
import io.github.rajvinodh.orbit.domain.TestCase;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OrbitDeviceLogger implements LogCatListener {

  private static final String TEST_RUNNER = "TestRunner";
  private static final Pattern MSG_START = Pattern.compile("started: (\\S+)\\((\\S+)\\)");
  private static final Pattern MSG_END = Pattern.compile("finished: (\\S+)\\((\\S+)\\)");

  private final List<LogCatMessage> buffer = new ArrayList<>();
  private final LogCatReceiverTask task;
  private final int logcatLevel;
  private final String appPackage;

  public OrbitDeviceLogger(IDevice device, int logcatLevel, String appPackage) {
    this.logcatLevel = logcatLevel;
    this.appPackage = appPackage;
    task = new LogCatReceiverTask(device);
    task.addLogCatListener(this);
    new Thread(task, "orbit-logcat-" + device.getSerialNumber()).start();
  }

  @Override
  public synchronized void log(List<LogCatMessage> messages) {
    buffer.addAll(messages);
  }

  /** Stops the receiver and returns buffered messages parsed per TestCase. */
  public Map<TestCase, List<LogCatMessage>> stop() {
    task.stop();
    return parseBuffer();
  }

  private synchronized Map<TestCase, List<LogCatMessage>> parseBuffer() {
    Map<TestCase, List<LogCatMessage>> result = new LinkedHashMap<>();
    TestCase current = null;
    int pid = -1;

    for (LogCatMessage msg : buffer) {
      String tag = msg.getHeader().getTag();
      int msgPid = msg.getHeader().getPid();
      int priority = msg.getHeader().getLogLevel().getPriority();

      if (TEST_RUNNER.equals(tag)) {
        Matcher start = MSG_START.matcher(msg.getMessage());
        if (start.matches()) {
          current = new TestCase(start.group(2), start.group(1));
          pid = msgPid;
          result.computeIfAbsent(current, k -> new ArrayList<>()).add(msg);
          continue;
        }
        Matcher end = MSG_END.matcher(msg.getMessage());
        if (end.matches() && current != null) {
          result.get(current).add(msg);
          current = null;
          pid = -1;
          continue;
        }
      }

      if (current == null) continue;

      boolean fromTestProcess = msgPid == pid && priority >= logcatLevel;
      boolean fromApp = appPackage.equals(msg.getHeader().getAppName()) && priority >= logcatLevel;
      if (fromTestProcess || fromApp) {
        result.get(current).add(msg);
      }
    }

    return result;
  }
}
