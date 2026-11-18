package io.github.rajvinodh.orbit.client.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Clears the device logcat buffer before the annotated test runs. Useful for isolating logcat
 * output to a single test.
 *
 * <pre>{@code
 * @Test
 * @ClearLogcat
 * public void testProducesCleanLogs() { ... }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface ClearLogcat {}
