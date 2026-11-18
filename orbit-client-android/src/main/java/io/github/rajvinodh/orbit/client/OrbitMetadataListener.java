package io.github.rajvinodh.orbit.client;

import android.annotation.SuppressLint;
import android.os.Bundle;
import androidx.test.internal.runner.listener.InstrumentationRunListener;
import io.github.rajvinodh.orbit.client.annotation.ClearAppData;
import io.github.rajvinodh.orbit.client.annotation.ClearLogcat;
import io.github.rajvinodh.orbit.client.annotation.ExternalId;
import java.util.ArrayList;
import java.util.List;
import org.junit.runner.Description;

/**
 * Registered via {@code -e listener io.github.rajvinodh.orbit.client.OrbitMetadataListener} on the
 * discovery ({@code log=true}) run. Unlike a JUnit Rule, an InstrumentationRunListener receives
 * {@link #testStarted} even in log-only mode, so annotations are always readable.
 *
 * <p>Sends a status-1 (in-progress) bundle so annotation data accumulates into {@code testEnded}'s
 * metric map on the runner side without triggering a lifecycle event.
 */
public class OrbitMetadataListener extends InstrumentationRunListener {

  @SuppressLint("RestrictedApi")
  @Override
  public void testStarted(Description description) throws Exception {
    super.testStarted(description);

    Bundle bundle = new Bundle();
    List<String> annotations = new ArrayList<>();

    ExternalId extId = description.getAnnotation(ExternalId.class);
    if (extId != null) {
      annotations.add("ExternalId");
      bundle.putString(OrbitRule.KEY_EXTERNAL_ID, extId.value());
    }

    ClearAppData clearAppData = description.getAnnotation(ClearAppData.class);
    if (clearAppData != null) {
      annotations.add("ClearAppData");
      String action;
      if (clearAppData.before() && clearAppData.after()) action = "BOTH";
      else if (clearAppData.before()) action = "BEFORE";
      else action = "AFTER";
      bundle.putString(OrbitRule.KEY_CLEAR_APP_DATA, action);
    }

    if (description.getAnnotation(ClearLogcat.class) != null) {
      annotations.add("ClearLogCat");
    }

    bundle.putString(OrbitRule.KEY_ANNOTATIONS, String.join(",", annotations));

    // Status 1 = in-progress: accumulated into testEnded's metric map without
    // triggering any test lifecycle event in ddmlib's InstrumentationResultParser.
    getInstrumentation().sendStatus(1, bundle);
  }
}
