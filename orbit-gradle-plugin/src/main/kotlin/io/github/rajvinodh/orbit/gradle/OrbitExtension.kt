package io.github.rajvinodh.orbit.gradle

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty

/**
 * The `orbit { }` DSL. Teams keep their fixed values in an `orbit.gradle` inside their own Android
 * project. Any value can be overridden for a single run with `-Porbit.<name>=<value>` (see
 * [GradleOverrides]).
 *
 * Unset values fall back to the orbit-runner defaults. `appPackage`, `testPackage` and `testRunner`
 * are read from the test APK when they are not set.
 */
abstract class OrbitExtension {

  // ── APKs (prebuilt mode / `orbitTest` only; variant tasks use the APKs AGP builds) ──
  abstract val testApk: RegularFileProperty
  abstract val targetApk: RegularFileProperty
  /** Extra APKs installed before the run (e.g. a test orchestrator or helper apps). */
  abstract val otherApks: ConfigurableFileCollection

  // ── App identity (prebuilt mode; inferred from the test APK when unset) ──
  abstract val appPackage: Property<String>
  abstract val testPackage: Property<String>
  abstract val testRunner: Property<String>

  // ── Devices ──
  abstract val devices: SetProperty<String>
  abstract val skipDevices: SetProperty<String>
  abstract val allowNoDevices: Property<Boolean>

  // ── Timing ──
  /** ISO-8601 duration, e.g. "PT10M". */
  abstract val adbTimeout: Property<String>
  abstract val availabilityTimeoutMin: Property<Long>

  // ── Test scope ──
  abstract val className: Property<String>
  abstract val methodName: Property<String>
  abstract val instrumentationArgs: MapProperty<String, String>
  /** Comma-separated external IDs, or "@/path/to/ids.txt". */
  abstract val externalIds: Property<String>

  // ── Execution ──
  /** "DISTRIBUTE" (each test on one device) or "FANOUT" (all tests on every device). */
  abstract val executionMode: Property<String>
  abstract val grantAll: Property<Boolean>
  abstract val clearAppData: Property<Boolean>
  abstract val coverage: Property<Boolean>

  // ── Observability ──
  abstract val trackLogcat: Property<Boolean>
  /** V/D/I/W/E */
  abstract val logcatLevel: Property<String>
  abstract val recordVideo: Property<Boolean>
  abstract val trackNetworkLogs: Property<Boolean>

  // ── Output ──
  abstract val outputDir: DirectoryProperty
  abstract val title: Property<String>

  // ── Appium Settings ──
  abstract val appiumSettingsVersion: Property<String>
  abstract val noAppiumSettings: Property<Boolean>

  // ── Misc ──
  abstract val debug: Property<Boolean>

  // ── Gradle-only ──
  /** When true, failing tests do not fail the build. */
  abstract val ignoreFailures: Property<Boolean>
  /** orbit-runner version to resolve. Defaults to the plugin's own version. */
  abstract val runnerVersion: Property<String>
  /** Android SDK. Defaults to AGP's SDK (variant tasks), else ANDROID_HOME / ANDROID_SDK_ROOT. */
  abstract val sdkDir: DirectoryProperty
  /** Max heap of the separate JVM that runs Orbit. */
  abstract val workerMaxHeap: Property<String>
}
