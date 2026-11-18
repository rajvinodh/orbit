import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.vanniktech.maven.publish.SonatypeHost
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform

plugins {
  kotlin("jvm")
  kotlin("plugin.lombok")
  java
  application
  jacoco
  id("com.vanniktech.maven.publish")
  id("com.github.johnrengelman.shadow") version "8.1.1"
}

kotlin { jvmToolchain(17) }

java {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
}

// Integration test source set
sourceSets {
  create("integrationTest") {
    kotlin { srcDir("src/integrationTest/kotlin") }
    compileClasspath += sourceSets["main"].output + configurations["testRuntimeClasspath"]
    runtimeClasspath += output + compileClasspath
  }

  // E2E source set — runs against a live AVD in CI (reactivecircus/android-emulator-runner)
  create("e2eTest") {
    kotlin { srcDir("src/e2eTest/kotlin") }
    compileClasspath += sourceSets["main"].output + configurations["testRuntimeClasspath"]
    runtimeClasspath += output + compileClasspath
  }
}

val integrationTestImplementation by
  configurations.getting { extendsFrom(configurations["testImplementation"]) }

val e2eTestImplementation by
  configurations.getting { extendsFrom(configurations["testImplementation"]) }

application { mainClass.set("io.github.rajvinodh.orbit.cli.OrbitCommandKt") }

// ── React frontend bundling ───────────────────────────────────────────────────

val frontendDir = rootProject.file("orbit-report")
val isWindows = DefaultNativePlatform.getCurrentOperatingSystem().isWindows

val buildFrontend by
  tasks.registering(Exec::class) {
    description = "Builds the orbit-report React SPA"
    group = "build"
    // Local copy: a lambda that reads a script-level val drags the script into the config cache.
    val dir = frontendDir
    onlyIf { dir.exists() }
    workingDir(frontendDir)
    commandLine(if (isWindows) "npm.cmd" else "npm", "run", "build")
    inputs.dir(frontendDir.resolve("src"))
    inputs.files(
      frontendDir.resolve("package.json"),
      frontendDir.resolve("vite.config.ts"),
      frontendDir.resolve("index.html"),
    )
    outputs.dir(frontendDir.resolve("dist"))
  }

val frontendResources = layout.buildDirectory.dir("generated-resources")

val copyFrontendResources by
  tasks.registering(Copy::class) {
    description = "Copies built frontend assets into the JAR resource tree"
    group = "build"
    dependsOn(buildFrontend)
    val dist = frontendDir.resolve("dist")
    onlyIf { dist.exists() }
    from(frontendDir.resolve("dist")) { include("index.html", "main.js", "main.css") }
    into(frontendResources.map { it.dir("orbit-report") })
  }

sourceSets.main { resources.srcDir(frontendResources) }

tasks.named("processResources") { dependsOn(copyFrontendResources) }

dependencies {
  // DDMLib — ADB abstraction
  implementation("com.android.tools.ddms:ddmlib:30.3.0")

  // Lombok — domain model boilerplate elimination
  compileOnly("org.projectlombok:lombok:1.18.30")
  annotationProcessor("org.projectlombok:lombok:1.18.30")
  testCompileOnly("org.projectlombok:lombok:1.18.30")
  testAnnotationProcessor("org.projectlombok:lombok:1.18.30")

  // CLI
  implementation("com.github.ajalt.clikt:clikt:4.2.1")

  // Utilities
  implementation("com.google.guava:guava:32.1.3-jre")
  implementation("commons-io:commons-io:2.15.1")

  // JaCoCo — coverage .ec file merging
  implementation("org.jacoco:org.jacoco.core:0.8.11")

  // JSON for report
  implementation("com.google.code.gson:gson:2.10.1")

  // APK manifest parsing — infers app/test package + runner from the test APK
  implementation("net.dongliu:apk-parser:2.6.10")

  // Nullability annotations
  compileOnly("org.jetbrains:annotations:24.1.0")

  // Logging
  implementation("org.slf4j:slf4j-api:2.0.9")
  implementation("ch.qos.logback:logback-classic:1.4.14")

  // Test
  testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
  testImplementation("io.mockk:mockk:1.13.8")
  testImplementation("org.assertj:assertj-core:3.24.2")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
  useJUnitPlatform()
  finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
  dependsOn(tasks.test)
  reports {
    html.required.set(true)
    xml.required.set(true)
  }
}

val integrationTest by
  tasks.registering(Test::class) {
    description = "Runs integration tests."
    group = "verification"
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
    useJUnitPlatform()
  }

val e2eTest by
  tasks.registering(Test::class) {
    description =
      "Runs E2E tests against a live AVD (requires ANDROID_HOME and a connected emulator)."
    group = "verification"
    testClassesDirs = sourceSets["e2eTest"].output.classesDirs
    classpath = sourceSets["e2eTest"].runtimeClasspath
    useJUnitPlatform()
    // Output dir picked up by e2e.yml artifact upload step
    systemProperty(
      "orbit.e2e.outputDir",
      layout.buildDirectory.dir("e2e-output").get().asFile.absolutePath,
    )
    // Always re-run — result depends on live device state, not source inputs
    outputs.upToDateWhen { false }
    testLogging {
      showStandardStreams = true
      events("passed", "skipped", "failed")
    }
  }

tasks.named<ShadowJar>("shadowJar") {
  archiveClassifier.set("")
  mergeServiceFiles()
}

// ── Maven publication ─────────────────────────────────────────────────────────
// Thin jar + POM dependencies (+ sources/javadoc jars, signed) to Maven Central. Consumed by
// orbit-gradle-plugin, which launches it as its own JVM. The fat CLI jar (shadowJar) is not
// published. Coordinates and POM come from gradle.properties.

// Shadow adds its (classifier-less) fat jar to the java component, which would clash with the
// thin jar's coordinates. Publish only the thin jar.
(components["java"] as AdhocComponentWithVariants).withVariantsFromConfiguration(
  configurations["shadowRuntimeElements"]
) {
  skip()
}

mavenPublishing {
  publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
  // Sign only when a key is configured, so publishToMavenLocal works without one.
  if (providers.gradleProperty("signingInMemoryKey").isPresent) signAllPublications()
}

// The sources jar includes the generated report resources dir, so it must run after the copy.
tasks.matching { it.name == "sourcesJar" }.configureEach { dependsOn(copyFrontendResources) }
