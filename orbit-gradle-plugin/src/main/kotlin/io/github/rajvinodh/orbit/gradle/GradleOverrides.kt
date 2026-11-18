package io.github.rajvinodh.orbit.gradle

import java.io.File
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider

/**
 * Reads `-Porbit.<name>` (or `orbit.<name>` in gradle.properties) per-run overrides.
 *
 * Precedence: `-Porbit.<name>` > `orbit { }` (the project's orbit.gradle) > plugin default.
 *
 * Formats:
 * - booleans: `true` / `false`
 * - lists (`devices`, `skipDevices`): comma-separated, e.g. `emulator-5554,emulator-5556`
 * - maps (`instrumentationArgs`): `key=value` pairs, comma-separated, e.g. `mock=true,env=qa`.
 *   These are merged on top of the `orbit { }` map instead of replacing it.
 * - files (`testApk`, `targetApk`, `outputDir`, `sdkDir`): absolute, or relative to the root
 *   project directory (where `./gradlew` is run)
 */
internal class GradleOverrides(project: Project) {

  // Captured up front so provider lambdas never touch `project` at execution time.
  private val providers = project.providers
  private val rootDir: File = project.rootDir
  private val projectDirectory: Directory = project.layout.projectDirectory

  fun string(name: String): Provider<String> =
    providers.gradleProperty("$PREFIX$name").map { it.trim() }.filter { it.isNotEmpty() }

  fun bool(name: String): Provider<Boolean> = string(name).map { it.toBooleanStrict() }

  fun long(name: String): Provider<Long> = string(name).map { it.toLong() }

  fun list(name: String): Provider<Set<String>> = string(name).map { parseList(it).toSet() }

  fun map(name: String): Provider<Map<String, String>> = string(name).map { parseArgs(it) }

  fun file(name: String): Provider<File> = string(name).map { resolve(it) }

  fun regularFile(name: String): Provider<RegularFile> =
    file(name).map { projectDirectory.file(it.absolutePath) }

  fun directory(name: String): Provider<Directory> =
    file(name).map { projectDirectory.dir(it.absolutePath) }

  private fun resolve(path: String): File {
    val f = File(path)
    return if (f.isAbsolute) f else File(rootDir, path)
  }

  companion object {
    const val PREFIX = "orbit."

    fun parseList(value: String): List<String> =
      value.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    /** `k1=v1,k2=v2` → map. Each pair is split at the first `=`, so values may contain `=`. */
    fun parseArgs(value: String): Map<String, String> =
      parseList(value).associate { pair ->
        val eq = pair.indexOf('=')
        require(eq > 0) { "Invalid ${PREFIX}instrumentationArgs entry '$pair': expected key=value" }
        pair.substring(0, eq).trim() to pair.substring(eq + 1).trim()
      }
  }
}
