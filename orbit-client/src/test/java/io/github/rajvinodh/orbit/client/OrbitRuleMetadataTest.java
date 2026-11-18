package io.github.rajvinodh.orbit.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rajvinodh.orbit.client.annotation.ClearAppData;
import io.github.rajvinodh.orbit.client.annotation.ClearLogcat;
import io.github.rajvinodh.orbit.client.annotation.ExternalId;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.runner.Description;

class OrbitRuleMetadataTest {

  // ── @ExternalId ────────────────────────────────────────────────────────

  @Test
  void externalId_annotation_is_detected() {
    Map<String, String> meta = buildMetadata(externalId("C1234"));

    assertThat(meta.get(OrbitRule.KEY_EXTERNAL_ID)).isEqualTo("C1234");
    assertThat(annotations(meta)).contains("ExternalId");
  }

  @Test
  void no_externalId_annotation_produces_null() {
    Map<String, String> meta = buildMetadata();

    assertThat(meta.get(OrbitRule.KEY_EXTERNAL_ID)).isNull();
  }

  // ── @ClearAppData ──────────────────────────────────────────────────────

  @Test
  void clearAppData_before_only_produces_BEFORE() {
    Map<String, String> meta = buildMetadata(clearAppData(true, false));

    assertThat(meta.get(OrbitRule.KEY_CLEAR_APP_DATA)).isEqualTo("BEFORE");
    assertThat(annotations(meta)).contains("ClearAppData");
  }

  @Test
  void clearAppData_after_only_produces_AFTER() {
    assertThat(buildMetadata(clearAppData(false, true)).get(OrbitRule.KEY_CLEAR_APP_DATA))
        .isEqualTo("AFTER");
  }

  @Test
  void clearAppData_both_produces_BOTH() {
    assertThat(buildMetadata(clearAppData(true, true)).get(OrbitRule.KEY_CLEAR_APP_DATA))
        .isEqualTo("BOTH");
  }

  @Test
  void no_clearAppData_produces_null_action() {
    assertThat(buildMetadata().get(OrbitRule.KEY_CLEAR_APP_DATA)).isNull();
  }

  // ── @ClearLogcat ───────────────────────────────────────────────────────

  @Test
  void clearLogcat_annotation_is_detected() {
    Map<String, String> meta = buildMetadata(clearLogcat());

    assertThat(annotations(meta)).contains("ClearLogCat");
  }

  // ── combined annotations ───────────────────────────────────────────────

  @Test
  void all_annotations_combined() {
    Map<String, String> meta =
        buildMetadata(externalId("PROJ-99"), clearAppData(true, false), clearLogcat());

    assertThat(meta.get(OrbitRule.KEY_EXTERNAL_ID)).isEqualTo("PROJ-99");
    assertThat(meta.get(OrbitRule.KEY_CLEAR_APP_DATA)).isEqualTo("BEFORE");
    assertThat(annotations(meta))
        .containsExactlyInAnyOrder("ExternalId", "ClearAppData", "ClearLogCat");
  }

  @Test
  void no_annotations_produces_empty_annotation_list() {
    Map<String, String> meta = buildMetadata();

    assertThat(annotations(meta)).isEmpty();
    assertThat(meta.get(OrbitRule.KEY_EXTERNAL_ID)).isNull();
    assertThat(meta.get(OrbitRule.KEY_CLEAR_APP_DATA)).isNull();
  }

  // ── Orbit path helpers ─────────────────────────────────────────────────

  @Test
  void screenshotDir_uses_class_and_method() {
    OrbitRule.TestInfo info = new OrbitRule.TestInfo("LoginTest", "testInvalid", null);
    assertThat(Orbit.screenshotDir(info).getPath())
        .isEqualTo("/sdcard/app_orbit-screenshots/LoginTest/testInvalid");
  }

  @Test
  void fileDir_uses_class_and_method() {
    OrbitRule.TestInfo info = new OrbitRule.TestInfo("LoginTest", "testInvalid", null);
    assertThat(Orbit.fileDir(info).getPath())
        .isEqualTo("/sdcard/app_orbit-files/LoginTest/testInvalid");
  }

  @Test
  void sanitise_replaces_special_chars() {
    assertThat(Orbit.sanitise("step 1: login")).isEqualTo("step_1__login");
    assertThat(Orbit.sanitise("valid-name_01.png")).isEqualTo("valid-name_01.png");
  }

  // ── helpers ────────────────────────────────────────────────────────────

  private Map<String, String> buildMetadata(Annotation... annotations) {
    return new OrbitRule(null).buildMetadata(descriptionWith(annotations));
  }

  private Set<String> annotations(Map<String, String> meta) {
    String csv = meta.get(OrbitRule.KEY_ANNOTATIONS);
    if (csv == null || csv.isEmpty()) return new HashSet<>();
    return new HashSet<>(Arrays.asList(csv.split(",")));
  }

  private Description descriptionWith(Annotation... annotations) {
    return Description.createTestDescription(OrbitRuleMetadataTest.class, "dummyTest", annotations);
  }

  private static ExternalId externalId(String value) {
    return new ExternalId() {
      public Class<? extends Annotation> annotationType() {
        return ExternalId.class;
      }

      public String value() {
        return value;
      }
    };
  }

  private static ClearAppData clearAppData(boolean before, boolean after) {
    return new ClearAppData() {
      public Class<? extends Annotation> annotationType() {
        return ClearAppData.class;
      }

      public boolean before() {
        return before;
      }

      public boolean after() {
        return after;
      }
    };
  }

  private static ClearLogcat clearLogcat() {
    return new ClearLogcat() {
      public Class<? extends Annotation> annotationType() {
        return ClearLogcat.class;
      }
    };
  }
}
