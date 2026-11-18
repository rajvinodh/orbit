package io.github.rajvinodh.orbit.gradle

import java.io.File
import java.net.URI
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

/**
 * Runs Orbit against one test APK + one target APK. It is used both for the prebuilt `orbitTest`
 * task and for the per-variant `orbit<Variant>AndroidTest` tasks; only the way the APKs and
 * packages are wired differs (see [OrbitPlugin] and [AndroidVariantWiring]).
 *
 * The runner executes in its own JVM (the orbit-runner CLI main) whose classpath is
 * [runnerClasspath], so the runner's ddmlib/guava/slf4j never meet the build's (AGP's, Gradle's)
 * copies.
 */
abstract class OrbitTestTask : DefaultTask() {

  init {
    group = "verification"
    // Results depend on live device state, not just inputs.
    outputs.upToDateWhen { false }
  }

  // ── APKs ──
  @get:Optional
  @get:InputFile
  @get:PathSensitive(PathSensitivity.NONE)
  abstract val testApk: RegularFileProperty

  @get:Optional
  @get:InputFile
  @get:PathSensitive(PathSensitivity.NONE)
  abstract val targetApk: RegularFileProperty

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.NONE)
  abstract val otherApks: ConfigurableFileCollection

  // ── App identity ──
  @get:Optional @get:Input abstract val appPackage: Property<String>
  @get:Optional @get:Input abstract val testPackage: Property<String>
  @get:Optional @get:Input abstract val testRunner: Property<String>

  // ── Devices ──
  @get:Input abstract val devices: SetProperty<String>
  @get:Input abstract val skipDevices: SetProperty<String>
  @get:Optional @get:Input abstract val allowNoDevices: Property<Boolean>

  // ── Timing ──
  @get:Optional @get:Input abstract val adbTimeout: Property<String>
  @get:Optional @get:Input abstract val availabilityTimeoutMin: Property<Long>

  // ── Test scope ──
  @get:Optional @get:Input abstract val className: Property<String>
  @get:Optional @get:Input abstract val methodName: Property<String>
  @get:Input abstract val instrumentationArgs: MapProperty<String, String>
  @get:Optional @get:Input abstract val externalIds: Property<String>

  // ── Execution ──
  @get:Optional @get:Input abstract val executionMode: Property<String>
  @get:Optional @get:Input abstract val grantAll: Property<Boolean>
  @get:Optional @get:Input abstract val clearAppData: Property<Boolean>
  @get:Optional @get:Input abstract val coverage: Property<Boolean>

  // ── Observability ──
  @get:Optional @get:Input abstract val trackLogcat: Property<Boolean>
  @get:Optional @get:Input abstract val logcatLevel: Property<String>
  @get:Optional @get:Input abstract val recordVideo: Property<Boolean>
  @get:Optional @get:Input abstract val trackNetworkLogs: Property<Boolean>

  // ── Output ──
  /** Internal: the runner wipes and rewrites it on every run. */
  @get:Internal abstract val outputDir: DirectoryProperty
  @get:Optional @get:Input abstract val title: Property<String>

  // ── Appium Settings ──
  @get:Optional @get:Input abstract val appiumSettingsVersion: Property<String>
  @get:Optional @get:Input abstract val noAppiumSettings: Property<Boolean>

  // ── Misc ──
  @get:Optional @get:Input abstract val debug: Property<Boolean>
  @get:Input abstract val ignoreFailures: Property<Boolean>
  @get:Internal abstract val sdkDir: DirectoryProperty
  @get:Input abstract val workerMaxHeap: Property<String>

  /** orbit-runner and its dependencies (configuration `orbitRunner`). */
  @get:Classpath abstract val runnerClasspath: ConfigurableFileCollection

  @get:Inject abstract val execOperations: ExecOperations

  companion object {
    const val RUNNER_MAIN_CLASS = "io.github.rajvinodh.orbit.cli.OrbitCommandKt"
  }

  @TaskAction
  fun runOrbit() {
    val testApkFile =
      testApk.orNull?.asFile
        ?: throw GradleException(
          "Orbit: no test APK. Pass -Porbit.testApk=<path> or set orbit { testApk = file(\"...\") }."
        )
    val targetApkFile =
      targetApk.orNull?.asFile
        ?: throw GradleException(
          "Orbit: no target (app) APK. Pass -Porbit.targetApk=<path> or set " +
            "orbit { targetApk = file(\"...\") }."
        )
    if (methodName.isPresent && !className.isPresent) {
      throw GradleException("Orbit: methodName '${methodName.get()}' requires className.")
    }

    val output = outputDir.get().asFile
    val cliArgs = buildCliArgs(testApkFile, targetApkFile, output)

    // A fresh JVM per run, entering through the same main() as the CLI. Logback works as in the
    // CLI (progress streams to the console), ADB state never leaks between runs, and the outcome
    // comes back as the CLI exit code: 0 = all passed, 1 = test failures, anything else = error.
    val result =
      execOperations.javaexec {
        classpath = runnerClasspath
        mainClass.set(RUNNER_MAIN_CLASS)
        maxHeapSize = workerMaxHeap.get()
        args(cliArgs)
        isIgnoreExitValue = true
      }

    val report = File(output, "orbit-report/index.html")
    when (result.exitValue) {
      0 -> logger.lifecycle("Orbit report: ${clickableUrl(report)}")
      1 -> {
        val message = "Orbit: tests failed. See ${clickableUrl(report)}"
        if (ignoreFailures.get()) logger.warn(message) else throw GradleException(message)
      }
      else ->
        throw GradleException(
          "Orbit runner exited with code ${result.exitValue} (configuration or runtime error — " +
            "see the output above)."
        )
    }
  }

  /** Maps the task's inputs onto orbit-runner CLI options (see cli/OrbitCommand.kt). */
  private fun buildCliArgs(testApkFile: File, targetApkFile: File, output: File): List<String> {
    val args = mutableListOf<String>()
    fun opt(name: String, value: Property<*>) {
      value.orNull?.let { args += listOf(name, it.toString()) }
    }
    fun flag(name: String, value: Property<Boolean>) {
      if (value.getOrElse(false)) args += name
    }

    opt("--app-package", appPackage)
    opt("--test-package", testPackage)
    opt("--test-runner", testRunner)
    devices.get().forEach { args += listOf("--serial", it) }
    skipDevices.get().forEach { args += listOf("--skip-serial", it) }
    flag("--allow-no-devices", allowNoDevices)
    opt("--adb-timeout", adbTimeout)
    opt("--availability-timeout", availabilityTimeoutMin)
    opt("--class-name", className)
    opt("--method-name", methodName)
    instrumentationArgs.get().forEach { (k, v) -> args += listOf("-e", k, v) }
    opt("--external-ids", externalIds)
    opt("--execution-mode", executionMode)
    flag("--grant-all", grantAll)
    flag("--clear-app-data", clearAppData)
    flag("--coverage", coverage)
    flag("--logcat", trackLogcat)
    opt("--logcat-level", logcatLevel)
    flag("--video", recordVideo)
    flag("--network-logs", trackNetworkLogs)
    args += listOf("--output", output.absolutePath)
    sdkDir.orNull?.let { args += listOf("--sdk", it.asFile.absolutePath) }
    opt("--title", title)
    if (noAppiumSettings.getOrElse(false)) {
      args += "--no-appium-settings"
    } else {
      opt("--appium-settings-version", appiumSettingsVersion)
    }
    flag("--debug", debug)

    // Positional: TEST_APK then OTHER_APKS (target app first).
    args += testApkFile.absolutePath
    args += targetApkFile.absolutePath
    otherApks.files.forEach { args += it.absolutePath }
    return args
  }

  /**
   * A file URL that terminals recognise. File.toURI() gives "file:/x" on macOS, which consoles do
   * not detect as a link, so an empty authority is added ("file:///x").
   */
  private fun clickableUrl(file: File): String =
    URI("file", "", file.toURI().path, null, null).toString()
}
