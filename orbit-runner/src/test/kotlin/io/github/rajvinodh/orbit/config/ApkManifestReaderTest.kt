package io.github.rajvinodh.orbit.config

import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ApkManifestReaderTest {

  private fun manifest(instrumentation: String) =
    """
    <?xml version="1.0" encoding="utf-8"?>
    <manifest xmlns:android="http://schemas.android.com/apk/res/android"
        package="com.example.app.test">
      $instrumentation
      <application android:debuggable="true"/>
    </manifest>
    """
      .trimIndent()

  @Test
  fun `reads test package, target package and runner`() {
    val info =
      ApkManifestReader.parseManifest(
        manifest(
          """<instrumentation android:name="com.example.CustomRunner"
               android:targetPackage="com.example.app"/>"""
        )
      )

    assertThat(info.testPackage()).isEqualTo("com.example.app.test")
    assertThat(info.targetPackage()).isEqualTo("com.example.app")
    assertThat(info.runner()).isEqualTo("com.example.CustomRunner")
  }

  @Test
  fun `relative runner name is resolved against test package`() {
    val info =
      ApkManifestReader.parseManifest(
        manifest("""<instrumentation android:name=".MyRunner" android:targetPackage="x"/>""")
      )

    assertThat(info.runner()).isEqualTo("com.example.app.test.MyRunner")
  }

  @Test
  fun `manifest without instrumentation yields nulls`() {
    val info = ApkManifestReader.parseManifest(manifest(""))

    assertThat(info.testPackage()).isEqualTo("com.example.app.test")
    assertThat(info.targetPackage()).isNull()
    assertThat(info.runner()).isNull()
  }

  @Test
  fun `fillMissing leaves fully specified config untouched`() {
    val config =
      RunConfig.builder()
        .testApk(File("does-not-matter.apk"))
        .appPackage("a")
        .testPackage("a.test")
        .testRunner("com.example.CustomRunner")
        .build()

    assertThat(ApkManifestReader.fillMissing(config)).isSameAs(config)
  }

  @Test
  fun `fillMissing returns config unchanged when test APK is missing`() {
    val config = RunConfig.builder().testApk(File("missing.apk")).build()

    assertThat(ApkManifestReader.fillMissing(config)).isSameAs(config)
  }
}
