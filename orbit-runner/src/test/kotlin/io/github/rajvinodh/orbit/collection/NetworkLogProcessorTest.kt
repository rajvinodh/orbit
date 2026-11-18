package io.github.rajvinodh.orbit.collection

import com.android.ddmlib.IDevice
import io.github.rajvinodh.orbit.domain.TestCase
import io.github.rajvinodh.orbit.domain.TestResult
import io.github.rajvinodh.orbit.domain.TestStatus
import io.github.rajvinodh.orbit.listener.MulticastTestRunListener
import io.github.rajvinodh.orbit.orchestration.DeviceSession
import io.mockk.every
import io.mockk.mockk
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class NetworkLogProcessorTest {

  @TempDir lateinit var tempDir: File

  private val device = mockk<IDevice>(relaxed = true)
  private val serial = "emulator-5554"

  private val knownTest = TestCase("com.example.FooTest", "testNetwork")

  private lateinit var session: DeviceSession
  private lateinit var networkDir: File

  @BeforeEach
  fun setUp() {
    every { device.serialNumber } returns serial

    session = DeviceSession(serial, mockk<MulticastTestRunListener>(relaxed = true), null)
    val result =
      TestResult.builder().testCase(knownTest).status(TestStatus.FAIL).durationMs(800L).build()
    session.addTestResult(knownTest, result)

    networkDir = File(tempDir, "network/$serial")
    networkDir.mkdirs()
  }

  // ── parseTestCase ──────────────────────────────────────────────────────

  @Test
  fun `parseTestCase returns correct TestCase for valid db filename`() {
    val tc =
      NetworkLogProcessor.parseTestCase("com.example.FooTest-testNetwork-1712345678-chucker.db")
    assertThat(tc).isNotNull
    assertThat(tc!!.className).isEqualTo("com.example.FooTest")
    assertThat(tc.methodName).isEqualTo("testNetwork")
  }

  @Test
  fun `parseTestCase returns null for malformed db filename`() {
    assertThat(NetworkLogProcessor.parseTestCase("nots-chucker.db")).isNull()
    assertThat(NetworkLogProcessor.parseTestCase("foo.db")).isNull()
    assertThat(NetworkLogProcessor.parseTestCase("a-b-notdigit-chucker.db")).isNull()
  }

  // ── collect ────────────────────────────────────────────────────────────

  @Test
  fun `known test db is associated with its TestResult`() {
    val ts = System.currentTimeMillis()
    val db = File(networkDir, "com.example.FooTest-testNetwork-$ts-chucker.db")
    db.writeText("fake-db")

    val processor = NetworkLogProcessor(networkDir)
    processor.collect(device, session)

    val result = session.build().testResults[knownTest]!!
    assertThat(result.files).hasSize(1)
    assertThat(result.files[0].name).isEqualTo(db.name)
  }

  @Test
  fun `unknown test db is skipped and does not throw`() {
    val ts = System.currentTimeMillis()
    File(networkDir, "com.example.Other-noSuchTest-$ts-chucker.db").writeText("fake")

    val processor = NetworkLogProcessor(networkDir)
    processor.collect(device, session) // no exception

    session.build().testResults.values.forEach { r -> assertThat(r.files).isEmpty() }
  }

  @Test
  fun `malformed filename is skipped`() {
    File(networkDir, "garbage.db").writeText("x")

    val processor = NetworkLogProcessor(networkDir)
    processor.collect(device, session)

    session.build().testResults.values.forEach { r -> assertThat(r.files).isEmpty() }
  }

  @Test
  fun `multiple db files for same test are all associated`() {
    val ts1 = 1000L
    val ts2 = 2000L
    File(networkDir, "com.example.FooTest-testNetwork-$ts1-chucker.db").writeText("a")
    File(networkDir, "com.example.FooTest-testNetwork-$ts2-chucker.db").writeText("b")

    val processor = NetworkLogProcessor(networkDir)
    processor.collect(device, session)

    val result = session.build().testResults[knownTest]!!
    assertThat(result.files).hasSize(2)
  }

  @Test
  fun `non-existent network dir is handled gracefully`() {
    val processor = NetworkLogProcessor(File(tempDir, "nonexistent"))
    processor.collect(device, session) // no exception
  }
}
