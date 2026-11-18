package io.github.rajvinodh.orbit.gradle

import java.util.Properties
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.java.TargetJvmEnvironment
import org.gradle.api.tasks.TaskProvider

/**
 * `io.github.rajvinodh.orbit`
 *
 * Registers:
 * - `orbitTest`: runs prebuilt APKs (`orbit { testApk / targetApk }` or `-Porbit.testApk` /
 *   `-Porbit.targetApk`). Depends on no compile/assemble task.
 * - `orbit<Variant>AndroidTest`: one per androidTest variant when `com.android.application` is
 *   applied. It builds the app + test APKs, then runs them.
 */
class OrbitPlugin : Plugin<Project> {

  override fun apply(project: Project) {
    val extension = project.extensions.create(EXTENSION_NAME, OrbitExtension::class.java)
    applyConventions(project, extension)

    val runnerClasspath = createRunnerConfiguration(project, extension)
    val overrides = GradleOverrides(project)

    registerPrebuiltTask(project, extension, overrides, runnerClasspath)

    project.pluginManager.withPlugin("com.android.application") {
      AndroidVariantWiring.register(project, extension, overrides, runnerClasspath)
    }
  }

  private fun applyConventions(project: Project, ext: OrbitExtension) {
    ext.outputDir.convention(project.layout.buildDirectory.dir("orbit-output"))
    ext.ignoreFailures.convention(false)
    ext.runnerVersion.convention(pluginVersion())
    ext.workerMaxHeap.convention("1g")
  }

  /**
   * Resolvable configuration holding orbit-runner (+ transitive deps) for the worker classpath.
   * Attributes are set explicitly so resolution is unambiguous inside Android projects.
   */
  private fun createRunnerConfiguration(project: Project, ext: OrbitExtension): Configuration =
    project.configurations.create(RUNNER_CONFIGURATION) {
      description = "orbit-runner classpath used by Orbit test tasks"
      isCanBeConsumed = false
      isCanBeResolved = true
      isVisible = false
      attributes {
        attribute(
          Usage.USAGE_ATTRIBUTE,
          project.objects.named(Usage::class.java, Usage.JAVA_RUNTIME),
        )
        attribute(
          Category.CATEGORY_ATTRIBUTE,
          project.objects.named(Category::class.java, Category.LIBRARY),
        )
        attribute(
          Bundling.BUNDLING_ATTRIBUTE,
          project.objects.named(Bundling::class.java, Bundling.EXTERNAL),
        )
        attribute(
          LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
          project.objects.named(LibraryElements::class.java, LibraryElements.JAR),
        )
        attribute(
          TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE,
          project.objects.named(TargetJvmEnvironment::class.java, TargetJvmEnvironment.STANDARD_JVM),
        )
      }
      defaultDependencies {
        add(project.dependencies.create("$RUNNER_COORDINATES:${ext.runnerVersion.get()}"))
      }
    }

  private fun registerPrebuiltTask(
    project: Project,
    ext: OrbitExtension,
    overrides: GradleOverrides,
    runnerClasspath: Configuration,
  ): TaskProvider<OrbitTestTask> =
    project.tasks.register(PREBUILT_TASK, OrbitTestTask::class.java) {
      description =
        "Runs Orbit on prebuilt APKs (-Porbit.testApk / -Porbit.targetApk). Compiles nothing."
      configureCommon(this, ext, overrides, runnerClasspath)
      testApk.set(overrides.regularFile("testApk").orElse(ext.testApk))
      targetApk.set(overrides.regularFile("targetApk").orElse(ext.targetApk))
      appPackage.set(overrides.string("appPackage").orElse(ext.appPackage))
      testPackage.set(overrides.string("testPackage").orElse(ext.testPackage))
      testRunner.set(overrides.string("testRunner").orElse(ext.testRunner))
      sdkDir.set(overrides.directory("sdkDir").orElse(ext.sdkDir))
    }

  companion object {
    const val EXTENSION_NAME = "orbit"
    const val PREBUILT_TASK = "orbitTest"
    const val RUNNER_CONFIGURATION = "orbitRunner"
    const val RUNNER_COORDINATES = "io.github.rajvinodh:orbit-runner"

    /**
     * Wires everything shared by the prebuilt and variant tasks: `-Porbit.*` overrides on top of
     * `orbit { }`.
     */
    internal fun configureCommon(
      task: OrbitTestTask,
      ext: OrbitExtension,
      o: GradleOverrides,
      runnerClasspath: Configuration,
    ) {
      with(task) {
        otherApks.from(ext.otherApks)
        devices.set(o.list("devices").orElse(ext.devices))
        skipDevices.set(o.list("skipDevices").orElse(ext.skipDevices))
        allowNoDevices.set(o.bool("allowNoDevices").orElse(ext.allowNoDevices))
        adbTimeout.set(o.string("adbTimeout").orElse(ext.adbTimeout))
        availabilityTimeoutMin.set(
          o.long("availabilityTimeoutMin").orElse(ext.availabilityTimeoutMin)
        )
        className.set(o.string("className").orElse(ext.className))
        methodName.set(o.string("methodName").orElse(ext.methodName))
        // -P args are merged on top of the fixed orbit { } args rather than replacing them.
        instrumentationArgs.set(
          ext.instrumentationArgs.zip(o.map("instrumentationArgs").orElse(emptyMap())) { fixed, cli
            ->
            fixed + cli
          }
        )
        externalIds.set(o.string("externalIds").orElse(ext.externalIds))
        executionMode.set(o.string("executionMode").orElse(ext.executionMode))
        grantAll.set(o.bool("grantAll").orElse(ext.grantAll))
        clearAppData.set(o.bool("clearAppData").orElse(ext.clearAppData))
        coverage.set(o.bool("coverage").orElse(ext.coverage))
        trackLogcat.set(o.bool("trackLogcat").orElse(ext.trackLogcat))
        logcatLevel.set(o.string("logcatLevel").orElse(ext.logcatLevel))
        recordVideo.set(o.bool("recordVideo").orElse(ext.recordVideo))
        trackNetworkLogs.set(o.bool("trackNetworkLogs").orElse(ext.trackNetworkLogs))
        outputDir.set(o.directory("outputDir").orElse(ext.outputDir))
        title.set(o.string("title").orElse(ext.title))
        appiumSettingsVersion.set(
          o.string("appiumSettingsVersion").orElse(ext.appiumSettingsVersion)
        )
        noAppiumSettings.set(o.bool("noAppiumSettings").orElse(ext.noAppiumSettings))
        debug.set(o.bool("debug").orElse(ext.debug))
        ignoreFailures.set(o.bool("ignoreFailures").orElse(ext.ignoreFailures))
        workerMaxHeap.set(ext.workerMaxHeap)
        this.runnerClasspath.from(runnerClasspath)
      }
    }

    private fun pluginVersion(): String {
      val props = Properties()
      OrbitPlugin::class.java.getResourceAsStream("/orbit-gradle-plugin.properties")?.use {
        props.load(it)
      }
      return props.getProperty("version")
        ?: error("orbit-gradle-plugin.properties missing from the plugin jar")
    }
  }
}
