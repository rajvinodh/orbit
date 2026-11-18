package io.github.rajvinodh.orbit.gradle

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.BuiltArtifactsLoader
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile

/**
 * Registers `orbit<Variant>AndroidTest` for every application variant that has an androidTest
 * component, e.g. `orbitDebugAndroidTest`, `orbitFreeDebugAndroidTest`.
 *
 * The APKs come from AGP's public artifacts API (`SingleArtifact.APK`). Wiring those providers into
 * the task's inputs is what makes Gradle build (assemble) both APKs before Orbit runs. The packages
 * and runner come from the variant, so nothing has to be configured by hand.
 *
 * Only loaded when `com.android.application` is applied: AGP is compileOnly.
 */
internal object AndroidVariantWiring {

  fun register(
    project: Project,
    ext: OrbitExtension,
    overrides: GradleOverrides,
    runnerClasspath: Configuration,
  ) {
    val components = project.extensions.getByType(ApplicationAndroidComponentsExtension::class.java)
    val sdkDirectory = components.sdkComponents.sdkDirectory
    val projectDirectory = project.layout.projectDirectory

    components.onVariants { variant ->
      val androidTest = variant.androidTest ?: return@onVariants

      val appApks = variant.artifacts.get(SingleArtifact.APK)
      val appLoader = variant.artifacts.getBuiltArtifactsLoader()
      val testApks = androidTest.artifacts.get(SingleArtifact.APK)
      val testLoader = androidTest.artifacts.getBuiltArtifactsLoader()

      val taskName = "orbit" + androidTest.name.replaceFirstChar { it.uppercaseChar() }
      project.tasks.register(taskName, OrbitTestTask::class.java) {
        description = "Builds ${variant.name} + its androidTest APK, then runs them with Orbit."
        OrbitPlugin.configureCommon(this, ext, overrides, runnerClasspath)

        targetApk.set(appApks.map { apkIn(it, appLoader, projectDirectory) })
        testApk.set(testApks.map { apkIn(it, testLoader, projectDirectory) })
        appPackage.set(variant.applicationId)
        testPackage.set(androidTest.applicationId)
        testRunner.set(androidTest.instrumentationRunner)
        sdkDir.set(overrides.directory("sdkDir").orElse(ext.sdkDir).orElse(sdkDirectory))
      }
    }
  }

  /**
   * The APK inside an AGP APK output directory. With ABI/density splits, the universal (unfiltered)
   * APK is preferred, else the first one.
   */
  private fun apkIn(
    dir: Directory,
    loader: BuiltArtifactsLoader,
    projectDir: Directory,
  ): RegularFile {
    val artifacts =
      loader.load(dir) ?: error("Orbit: no APK metadata (output-metadata.json) in ${dir.asFile}")
    val element =
      artifacts.elements.firstOrNull { it.filters.isEmpty() }
        ?: artifacts.elements.firstOrNull()
        ?: error("Orbit: no APK found in ${dir.asFile}")
    return projectDir.file(element.outputFile)
  }
}
