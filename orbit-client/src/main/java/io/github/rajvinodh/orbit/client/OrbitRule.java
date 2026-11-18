package io.github.rajvinodh.orbit.client;

import android.app.Instrumentation;
import io.github.rajvinodh.orbit.client.annotation.ClearAppData;
import io.github.rajvinodh.orbit.client.annotation.ClearLogcat;
import io.github.rajvinodh.orbit.client.annotation.ExternalId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * JUnit 4 rule that:
 *
 * <ul>
 *   <li>Reports test metadata (annotations, externalId, clearAppDataAction) to the Orbit runner via
 *       {@code Instrumentation.sendStatus()} so the runner can build {@link
 *       io.github.rajvinodh.orbit.domain.TestMetadata} during discovery.
 *   <li>Exposes the current test context to {@link Orbit} static helpers via a thread-local so
 *       {@code Orbit.screenshot()} knows the instrumentation and path.
 * </ul>
 *
 * <pre>{@code
 * @Rule public OrbitRule orbit = new OrbitRule(InstrumentationRegistry.getInstrumentation());
 * }</pre>
 */
public class OrbitRule implements TestRule {

  // Keys must match what LogRecordingTestRunListener reads on the runner side
  static final String KEY_ANNOTATIONS = "annotations";
  static final String KEY_EXTERNAL_ID = "externalId";
  static final String KEY_CLEAR_APP_DATA = "clearAppDataAction";

  private static final ThreadLocal<TestInfo> CURRENT = new ThreadLocal<>();

  private final Instrumentation instrumentation;

  public OrbitRule(Instrumentation instrumentation) {
    this.instrumentation = instrumentation;
  }

  @Override
  public Statement apply(Statement base, Description description) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        TestInfo info =
            new TestInfo(
                description.getTestClass().getName(), description.getMethodName(), instrumentation);
        CURRENT.set(info);
        try {
          base.evaluate();
        } finally {
          CURRENT.remove();
        }
      }
    };
  }

  // ── metadata extraction (package-private for testing) ─────────────────

  /**
   * Pure-Java annotation extraction — kept here for unit testing without the Android runtime.
   * Discovery-time delivery is handled by {@link OrbitMetadataListener}.
   */
  Map<String, String> buildMetadata(Description description) {
    List<String> annotations = new ArrayList<>();
    String externalId = null;
    String clearAction = null;

    ExternalId extId = description.getAnnotation(ExternalId.class);
    if (extId != null) {
      externalId = extId.value();
      annotations.add("ExternalId");
    }

    ClearAppData clearAppData = description.getAnnotation(ClearAppData.class);
    if (clearAppData != null) {
      annotations.add("ClearAppData");
      if (clearAppData.before() && clearAppData.after()) clearAction = "BOTH";
      else if (clearAppData.before()) clearAction = "BEFORE";
      else if (clearAppData.after()) clearAction = "AFTER";
    }

    if (description.getAnnotation(ClearLogcat.class) != null) {
      annotations.add("ClearLogCat");
    }

    Map<String, String> meta = new LinkedHashMap<>();
    meta.put(KEY_ANNOTATIONS, String.join(",", annotations));
    if (externalId != null) meta.put(KEY_EXTERNAL_ID, externalId);
    if (clearAction != null) meta.put(KEY_CLEAR_APP_DATA, clearAction);
    return meta;
  }

  // ── thread-local access for Orbit static helpers ───────────────────────

  static TestInfo currentTest() {
    return CURRENT.get();
  }

  static final class TestInfo {
    final String className;
    final String methodName;
    final Instrumentation instrumentation;

    TestInfo(String className, String methodName, Instrumentation instrumentation) {
      this.className = className;
      this.methodName = methodName;
      this.instrumentation = instrumentation;
    }
  }
}
