package io.github.rajvinodh.orbit.client.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Triggers {@code pm clear <package>} before and/or after the annotated test. Requires the runner
 * to be configured with {@code --grant-all} or the app to have the necessary permissions.
 *
 * <pre>{@code
 * @Test
 * @ClearAppData(before = true)
 * public void testFreshInstall() { ... }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface ClearAppData {
  boolean before() default false;

  boolean after() default false;
}
