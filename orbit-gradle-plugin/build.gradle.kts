plugins {
  kotlin("jvm")
  // Gradle-style `Action<T>` lambdas with an implicit receiver (what `kotlin-dsl` sets up).
  kotlin("plugin.sam.with.receiver")
  `java-gradle-plugin`
  id("com.vanniktech.maven.publish")
}

// Plugin jar + plugin marker to Maven Central. Coordinates and POM come from gradle.properties.
mavenPublishing {
  publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL)
  // Sign only when a key is configured, so publishToMavenLocal works without one.
  if (providers.gradleProperty("signingInMemoryKey").isPresent) signAllPublications()
}

kotlin { jvmToolchain(17) }

samWithReceiver { annotation("org.gradle.api.HasImplicitReceiver") }

dependencies {
  // No dependency on orbit-runner: it is resolved per consuming project (configuration
  // `orbitRunner`) and launched as its own JVM through the CLI main, so its ddmlib/slf4j never
  // meet the build's copies.

  // AGP Variant API — compileOnly so the plugin also works in projects without AGP (prebuilt mode).
  compileOnly("com.android.tools.build:gradle-api:8.5.2")

  testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
  testImplementation("org.assertj:assertj-core:3.24.2")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

gradlePlugin {
  plugins {
    create("orbit") {
      id = "io.github.rajvinodh.orbit"
      implementationClass = "io.github.rajvinodh.orbit.gradle.OrbitPlugin"
      displayName = "Orbit"
      description = "Run Android instrumentation tests across multiple devices with Orbit"
    }
  }
}

// Bake the plugin version into a resource: it is the default orbit-runner version to resolve,
// keeping plugin and runner in lockstep.
val generatePluginProperties by
  tasks.registering(WriteProperties::class) {
    destinationFile.set(
      layout.buildDirectory.file("generated/plugin-resources/orbit-gradle-plugin.properties")
    )
    property("version", project.version.toString())
  }

sourceSets.main {
  resources.srcDir(generatePluginProperties.map { it.destinationFile.get().asFile.parentFile })
}

tasks.test { useJUnitPlatform() }
