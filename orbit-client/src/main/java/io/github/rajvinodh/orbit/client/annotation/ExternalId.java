package io.github.rajvinodh.orbit.client.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Tags a test with a TMS-agnostic external identifier (e.g. "C1234" for TestRail, "PROJ-42" for
 * Jira). The runner uses this for --external-ids filtering and populates TestResult.externalId for
 * the report.
 *
 * <pre>{@code
 * @Test
 * @ExternalId("C1234")
 * public void testLogin() { ... }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface ExternalId {
  String value();
}
