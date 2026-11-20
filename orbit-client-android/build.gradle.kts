import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.SonatypeHost

plugins {
  id("com.android.library")
  id("com.vanniktech.maven.publish")
}

android {
  namespace = "io.github.rajvinodh.orbit.client.android"
  compileSdk = 35

  defaultConfig { minSdk = 24 }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

dependencies {
  // Core orbit-client types (OrbitRule.KEY_*, annotations). `api` so the published POM declares
  // it and consumers get orbit-client transitively.
  api(project(":orbit-client"))
  // ATSL — properly resolved as AAR by the Android Gradle Plugin
  compileOnly("androidx.test:runner:1.6.2")
  compileOnly("junit:junit:4.13.2")
}

// ── Maven publication ─────────────────────────────────────────────────────────
// Release AAR + POM (+ sources/javadoc jars, signed) to Maven Central. Coordinates and POM come
// from gradle.properties.

mavenPublishing {
  configure(
    AndroidSingleVariantLibrary(variant = "release", sourcesJar = true, publishJavadocJar = true)
  )
  publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
  // Sign only when a key is configured, so publishToMavenLocal works without one.
  if (providers.gradleProperty("signingInMemoryKey").isPresent) signAllPublications()
}
