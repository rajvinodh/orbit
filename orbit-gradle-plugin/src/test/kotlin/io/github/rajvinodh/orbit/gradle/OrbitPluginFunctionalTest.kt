package io.github.rajvinodh.orbit.gradle

import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Plugin behaviour without AGP (prebuilt mode). Nothing here touches a device. */
class OrbitPluginFunctionalTest {

  @TempDir lateinit var projectDir: File

  @BeforeEach
  fun setUp() {
    File(projectDir, "settings.gradle").writeText("rootProject.name = 'consumer'\n")
    File(projectDir, "orbit.gradle")
      .writeText(
        """
        orbit {
            className = "com.fixed.FromOrbitGradle"
            executionMode = "DISTRIBUTE"
            grantAll = true
            instrumentationArgs = [listener: "com.Printer", mock: "false"]
        }
        """
          .trimIndent()
      )
    File(projectDir, "build.gradle")
      .writeText(
        """
        plugins { id 'io.github.rajvinodh.orbit' }
        apply from: 'orbit.gradle'

        // No repositories here: replace the default orbit-runner dependency with a stub so the
        // runner classpath resolves. Also covers pinning a custom runner.
        dependencies { orbitRunner files('runner-stub.jar') }

        tasks.register('printOrbit') {
            def t = tasks.named('orbitTest').get()
            doLast {
                println "className=" + t.className.get()
                println "executionMode=" + t.executionMode.get()
                println "grantAll=" + t.grantAll.get()
                println "devices=" + t.devices.get().join(',')
                println "args=" + new TreeMap(t.instrumentationArgs.get())
                println "testApk=" + (t.testApk.present ? t.testApk.get().asFile.name : '<none>')
            }
        }
        """
          .trimIndent()
      )
  }

  private fun gradle(vararg args: String) =
    GradleRunner.create().withProjectDir(projectDir).withPluginClasspath().withArguments(*args)

  @Test
  fun `registers prebuilt task and no variant tasks without AGP`() {
    val result = gradle("tasks", "--group", "verification").build()

    assertThat(result.output).contains("orbitTest")
    assertThat(result.output).doesNotContain("AndroidTest")
  }

  @Test
  fun `orbit gradle values are used when no -P is given`() {
    val result = gradle("printOrbit", "-q").build()

    assertThat(result.output)
      .contains("className=com.fixed.FromOrbitGradle")
      .contains("executionMode=DISTRIBUTE")
      .contains("grantAll=true")
      .contains("args=[listener:com.Printer, mock:false]")
      .contains("testApk=<none>")
  }

  @Test
  fun `-P overrides win and instrumentation args are merged`() {
    val result =
      gradle(
          "printOrbit",
          "-q",
          "-Porbit.className=com.cli.Override",
          "-Porbit.executionMode=FANOUT",
          "-Porbit.devices=emulator-5554, emulator-5556",
          "-Porbit.instrumentationArgs=mock=true,env=qa",
          "-Porbit.testApk=build/apks/test.apk",
        )
        .build()

    assertThat(result.output)
      .contains("className=com.cli.Override")
      .contains("executionMode=FANOUT")
      .contains("devices=emulator-5554,emulator-5556")
      .contains("args=[env:qa, listener:com.Printer, mock:true]")
      .contains("testApk=test.apk")
  }

  @Test
  fun `orbitTest without a test APK fails with a clear message`() {
    val result = gradle("orbitTest").buildAndFail()

    assertThat(result.output).contains("Orbit: no test APK").contains("-Porbit.testApk")
  }
}
