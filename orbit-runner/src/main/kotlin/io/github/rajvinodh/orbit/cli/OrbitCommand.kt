package io.github.rajvinodh.orbit.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.long
import io.github.rajvinodh.orbit.OrbitRunner
import io.github.rajvinodh.orbit.config.RunConfig
import io.github.rajvinodh.orbit.domain.ExecutionMode
import io.github.rajvinodh.orbit.selection.ExternalIdSelectionStrategy
import io.github.rajvinodh.orbit.selection.TestSelectionStrategy
import java.io.File
import java.time.Duration
import kotlin.system.exitProcess

class OrbitCommand :
  CliktCommand(name = "orbit", help = "Run Android instrumentation tests across multiple devices") {
  // --- Required ---
  private val testApk by argument("TEST_APK").file(mustExist = true)
  private val otherApks by argument("OTHER_APKS").file(mustExist = true).multiple()

  // --- App identity ---
  private val appPackage by
    option(
      "--app-package",
      "-p",
      help = "Application package under test. Default: read from the test APK's manifest",
    )
  private val testPackage by
    option(
      "--test-package",
      help = "Instrumentation (test) package. Default: read from the test APK's manifest",
    )
  private val testRunner by
    option(
      "--test-runner",
      help = "Instrumentation runner class. Default: read from the test APK's manifest",
    )

  // --- Devices ---
  private val serials by option("--serial", help = "Device serial (repeatable)").multiple()
  private val skipSerials by
    option("--skip-serial", help = "Device serial to skip (repeatable)").multiple()
  private val allowNoDevices by option("--allow-no-devices").flag()

  // --- Timing ---
  private val adbTimeout by
    option("--adb-timeout", help = "ADB command timeout, ISO-8601 e.g. PT10M")
      .convert { Duration.parse(it) }
      .default(Duration.ofMinutes(10))
  private val availabilityTimeout by
    option("--availability-timeout", help = "Max minutes to wait for a free device")
      .long()
      .default(30L)

  // --- Test scope ---
  private val className by option("--class-name", help = "Fully-qualified class to run")
  private val methodName by option("--method-name", help = "Method to run (requires --class-name)")
  private val instrumentationArgs by
    option("-e", help = "Instrumentation arg (repeatable): -e KEY VALUE").pair().multiple()

  // --- Execution ---
  private val executionMode by
    option(
        "--execution-mode",
        help = "DISTRIBUTE (default, each test on one device) or FANOUT (all tests on all devices)",
      )
      .convert { ExecutionMode.valueOf(it.uppercase()) }
      .default(ExecutionMode.DISTRIBUTE)
  private val grantAll by option("--grant-all").flag()
  private val clearAppData by
    option("--clear-app-data", help = "Run 'pm clear' before each test").flag()
  private val coverage by option("--coverage").flag()

  // --- Observability ---
  private val trackLogcat by
    option("--logcat", help = "Capture logcat; mapped to failed tests only").flag()
  private val logcatLevel by
    option("--logcat-level", help = "Min logcat level: V/D/I/W/E").default("I")
  private val recordVideo by option("--video").flag()
  private val trackNetworkLogs by
    option("--network-logs", help = "Capture Chucker DB per failed test").flag()

  // --- Test selection ---
  private val externalIds by
    option("--external-ids", help = "Comma-separated external IDs, or @/path/to/ids.txt")

  // --- Output ---
  private val output by
    option("--output", "-o", help = "Output directory (default: orbit-output)")
      .file()
      .default(File("orbit-output"))
  private val sdk by option("--sdk", help = "Android SDK path. Defaults to \$ANDROID_HOME").file()
  private val title by option("--title", help = "Report title. Default: '<package> · <date>'")

  // --- Appium Settings ---
  private val appiumSettingsVersion by
    option(
        "--appium-settings-version",
        help = "io.appium.settings version to install during provisioning (default: 7.0.24)",
      )
      .default("7.0.24")
  private val noAppiumSettings by
    option("--no-appium-settings", help = "Skip io.appium.settings installation entirely").flag()

  // --- Misc ---
  private val debug by option("--debug").flag()
  private val alwaysZero by
    option("--always-zero", help = "Always exit 0 regardless of test failures").flag()

  override fun run() {
    val selectionStrategy =
      externalIds?.let { ExternalIdSelectionStrategy.from(it) } ?: TestSelectionStrategy.ALL

    val builder =
      RunConfig.builder()
        .testApk(testApk)
        .otherApks(otherApks)
        .appPackage(appPackage)
        .testPackage(testPackage)
    testRunner?.let { builder.testRunner(it) }

    val config =
      builder
        .serials(serials.toSet())
        .skipSerials(skipSerials.toSet())
        .allowNoDevices(allowNoDevices)
        .adbTimeout(adbTimeout)
        .availabilityTimeoutMin(availabilityTimeout)
        .className(className)
        .methodName(methodName)
        .instrumentationArgs(instrumentationArgs.toMap())
        .executionMode(executionMode)
        .grantAll(grantAll)
        .clearAppDataBeforeEachTest(clearAppData)
        .codeCoverage(coverage)
        .trackLogcat(trackLogcat)
        .logcatLevel(RunConfig.logcatPriority(logcatLevel))
        .recordVideo(recordVideo)
        .trackNetworkLogs(trackNetworkLogs)
        .appiumSettingsVersion(if (noAppiumSettings) null else appiumSettingsVersion)
        .selectionStrategy(selectionStrategy)
        .output(output)
        .androidSdk(sdk ?: resolveAndroidHome())
        .reportTitle(title)
        .debug(debug)
        .build()

    val success = OrbitRunner(config).run()
    if (!success && !alwaysZero) exitProcess(1)
  }

  private fun resolveAndroidHome(): File? {
    val home = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
    return home?.let { File(it) }
  }
}

fun main(args: Array<String>) = OrbitCommand().main(args)
