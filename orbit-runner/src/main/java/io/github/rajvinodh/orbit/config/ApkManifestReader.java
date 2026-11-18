package io.github.rajvinodh.orbit.config;

import java.io.File;
import java.io.StringReader;
import javax.annotation.Nullable;
import javax.xml.parsers.DocumentBuilderFactory;
import lombok.extern.slf4j.Slf4j;
import net.dongliu.apk.parser.ApkFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * Reads the binary AndroidManifest.xml of a test (instrumentation) APK so callers only have to
 * supply APK paths. From the test APK's {@code <manifest package>} and its {@code <instrumentation
 * android:name android:targetPackage>} we get the test package, the runner class and the package of
 * the app under test.
 */
@Slf4j
public final class ApkManifestReader {

  public static final String DEFAULT_TEST_RUNNER = "androidx.test.runner.AndroidJUnitRunner";

  private ApkManifestReader() {}

  /** What an instrumentation APK declares about itself. */
  public record InstrumentationInfo(
      String testPackage, @Nullable String targetPackage, @Nullable String runner) {}

  /** Parses the test APK's manifest. Throws IllegalArgumentException if it cannot be read. */
  public static InstrumentationInfo readInstrumentation(File testApk) {
    try (ApkFile apk = new ApkFile(testApk)) {
      return parseManifest(apk.getManifestXml());
    } catch (Exception e) {
      throw new IllegalArgumentException(
          "Cannot read AndroidManifest.xml from " + testApk + ": " + e.getMessage(), e);
    }
  }

  /** Visible for testing — parses decoded manifest XML text. */
  static InstrumentationInfo parseManifest(String manifestXml) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(false); // attributes keep their "android:" prefix
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    Document doc =
        factory.newDocumentBuilder().parse(new InputSource(new StringReader(manifestXml)));

    Element manifest = doc.getDocumentElement();
    String testPackage = manifest.getAttribute("package");

    String targetPackage = null;
    String runner = null;
    NodeList instrumentations = manifest.getElementsByTagName("instrumentation");
    if (instrumentations.getLength() > 0) {
      Element instrumentation = (Element) instrumentations.item(0);
      runner = emptyToNull(instrumentation.getAttribute("android:name"));
      targetPackage = emptyToNull(instrumentation.getAttribute("android:targetPackage"));
      if (runner != null && runner.startsWith(".")) runner = testPackage + runner;
    }
    return new InstrumentationInfo(testPackage, targetPackage, runner);
  }

  /**
   * Fills {@code appPackage}, {@code testPackage} and {@code testRunner} from the test APK when
   * they were not supplied. The runner is replaced only when it is still the default and the APK
   * declares a different one, since only an instrumentation declared in the APK can be run anyway.
   * Values that were set explicitly are never changed. If parsing fails, the config is returned
   * unchanged.
   */
  public static RunConfig fillMissing(RunConfig config) {
    boolean needsApp = config.getAppPackage() == null;
    boolean needsTest = config.getTestPackage() == null;
    boolean defaultRunner = DEFAULT_TEST_RUNNER.equals(config.getTestRunner());
    if (!needsApp && !needsTest && !defaultRunner) return config;
    if (config.getTestApk() == null || !config.getTestApk().isFile()) return config;

    InstrumentationInfo info;
    try {
      info = readInstrumentation(config.getTestApk());
    } catch (IllegalArgumentException e) {
      log.warn("{} — pass --app-package / --test-package explicitly", e.getMessage());
      return config;
    }

    RunConfig.RunConfigBuilder builder = config.toBuilder();
    if (needsApp && info.targetPackage() != null) {
      log.info("App package (from test APK): {}", info.targetPackage());
      builder.appPackage(info.targetPackage());
    }
    if (needsTest && !info.testPackage().isEmpty()) {
      log.info("Test package (from test APK): {}", info.testPackage());
      builder.testPackage(info.testPackage());
    }
    if (defaultRunner && info.runner() != null && !info.runner().equals(DEFAULT_TEST_RUNNER)) {
      log.info("Test runner (from test APK): {}", info.runner());
      builder.testRunner(info.runner());
    }
    return builder.build();
  }

  @Nullable
  private static String emptyToNull(String s) {
    return s == null || s.isEmpty() ? null : s;
  }
}
