package io.github.rajvinodh.orbit.device

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.IDevice
import com.android.ddmlib.IShellOutputReceiver
import com.android.ddmlib.InstallException
import io.github.rajvinodh.orbit.config.RunConfig
import io.mockk.*
import java.io.File
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DefaultDeviceProvisionerTest {

  private val adb = mockk<AndroidDebugBridge>(relaxed = true)
  private val connectionChecker = mockk<DeviceConnectionChecker>()
  private val device = mockk<IDevice>(relaxed = true)

  private val provisioner = DefaultDeviceProvisioner(adb, connectionChecker)

  private val testApk = File("app-test.apk")
  private val config =
    RunConfig.builder()
      .testApk(testApk)
      .appPackage("com.example.app")
      .appiumSettingsVersion(null) // skip Appium Settings provisioning — not under test here
      .build()

  @BeforeEach
  fun setup() {
    every { device.getSerialNumber() } returns "emulator-5554"
    // default: shell commands succeed (relaxed mock)
    // default: API level 33
    every { device.getProperty("ro.build.version.sdk") } returns "33"
  }

  @Test
  fun `installApks success returns SUCCESS`() {
    val result = provisioner.provision(device, config)
    assertThat(result).isEqualTo(ProvisionResult.SUCCESS)
  }

  @Test
  fun `installApks InstallException device reachable returns PROVISIONING_FAILED`() {
    every { device.installPackage(any(), any(), *anyVararg()) } throws InstallException("fail")
    every { connectionChecker.isAvailable(adb, "emulator-5554") } returns true

    val result = provisioner.provision(device, config)
    assertThat(result).isEqualTo(ProvisionResult.PROVISIONING_FAILED)
  }

  @Test
  fun `installApks InstallException device gone returns DISCONNECTED`() {
    every { device.installPackage(any(), any(), *anyVararg()) } throws InstallException("fail")
    every { connectionChecker.isAvailable(adb, "emulator-5554") } returns false

    val result = provisioner.provision(device, config)
    assertThat(result).isEqualTo(ProvisionResult.DISCONNECTED)
  }

  @Test
  fun `step order is install then clean then grant then clearLogcat`() {
    val order = mutableListOf<String>()

    every { device.installPackage(any(), any(), *anyVararg()) } answers { order += "install" }
    every {
      device.executeShellCommand(match { it.startsWith("rm -rf") }, any(), any(), any())
    } answers { order += "clean" }
    every {
      device.executeShellCommand(
        match { it.startsWith("pm grant") || it.startsWith("appops") },
        any(),
        any(),
        any(),
      )
    } answers { order += "grant" }
    every { device.executeShellCommand("logcat -c", any(), any(), any()) } answers
      {
        order += "logcat"
      }

    provisioner.provision(device, config)

    assertThat(order.first()).isEqualTo("install")
    assertThat(order).contains("clean", "grant", "logcat")
    assertThat(order.indexOf("install")).isLessThan(order.indexOf("clean"))
    assertThat(order.indexOf("clean")).isLessThan(order.indexOf("grant"))
    assertThat(order.indexOf("grant")).isLessThan(order.indexOf("logcat"))
  }

  @Test
  fun `grantAll=false results in no -g flag`() {
    val cfg = config // grantAll defaults to false
    val flag = provisioner.grantAllFlag(device, cfg)
    assertThat(flag).isEmpty()
  }

  @Test
  fun `grantAll=true API below 23 results in no -g flag`() {
    every { device.getProperty("ro.build.version.sdk") } returns "21"
    val cfg =
      RunConfig.builder().testApk(testApk).appPackage("com.example.app").grantAll(true).build()
    val flag = provisioner.grantAllFlag(device, cfg)
    assertThat(flag).isEmpty()
  }

  @Test
  fun `grantAll=true API 23 or above results in -g flag`() {
    every { device.getProperty("ro.build.version.sdk") } returns "23"
    val cfg =
      RunConfig.builder().testApk(testApk).appPackage("com.example.app").grantAll(true).build()
    val flag = provisioner.grantAllFlag(device, cfg)
    assertThat(flag).isEqualTo("-g")
  }

  @Test
  fun `API 23 causes READ and WRITE_EXTERNAL_STORAGE grants`() {
    every { device.getProperty("ro.build.version.sdk") } returns "23"
    val commands = captureShellCommands()

    provisioner.provision(device, config)

    assertThat(commands).anyMatch { it.contains("READ_EXTERNAL_STORAGE") }
    assertThat(commands).anyMatch { it.contains("WRITE_EXTERNAL_STORAGE") }
    assertThat(commands).noneMatch { it.contains("MANAGE_EXTERNAL_STORAGE") }
  }

  @Test
  fun `API 30 causes MANAGE_EXTERNAL_STORAGE grant in addition`() {
    every { device.getProperty("ro.build.version.sdk") } returns "30"
    val commands = captureShellCommands()

    provisioner.provision(device, config)

    assertThat(commands).anyMatch { it.contains("MANAGE_EXTERNAL_STORAGE") }
  }

  @Test
  fun `cleanDirectories sends rm -rf for each device dir`() {
    val commands = captureShellCommands()

    provisioner.provision(device, config)

    assertThat(commands.filter { it.startsWith("rm -rf") }).hasSize(3)
  }

  // --- helpers ---

  private fun captureShellCommands(): MutableList<String> {
    val commands = mutableListOf<String>()
    every {
      device.executeShellCommand(any(), any<IShellOutputReceiver>(), any<Long>(), any<TimeUnit>())
    } answers { commands += firstArg<String>() }
    return commands
  }
}
