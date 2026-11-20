import com.vanniktech.maven.publish.SonatypeHost

plugins {
  java
  jacoco
  id("com.vanniktech.maven.publish")
}

java {
  sourceCompatibility = JavaVersion.VERSION_17 // Android minSdk compat
  targetCompatibility = JavaVersion.VERSION_17
}

// Locate android.jar from ANDROID_HOME — required to compile Android API calls
val androidHome = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
val androidJar: File? =
  androidHome?.let { home ->
    File(home, "platforms")
      .listFiles()
      ?.filter { it.isDirectory && it.name.startsWith("android-") }
      ?.maxByOrNull { it.name }
      ?.let { File(it, "android.jar") }
      ?.takeIf { it.exists() }
  }

if (androidJar == null) {
  logger.warn(
    "orbit-client: ANDROID_HOME not set or android.jar not found — compileJava will be skipped."
  )
}

tasks.compileJava { enabled = androidJar != null }

dependencies {
  if (androidJar != null) {
    compileOnly(files(androidJar))
    testCompileOnly(files(androidJar))
    testRuntimeOnly(files(androidJar))
  }
  // JUnit 4 — orbit-client targets instrumentation tests
  compileOnly("junit:junit:4.13.2")
  testCompileOnly("junit:junit:4.13.2")
  testRuntimeOnly("junit:junit:4.13.2")
  // Lombok for any value objects
  compileOnly("org.projectlombok:lombok:1.18.30")
  annotationProcessor("org.projectlombok:lombok:1.18.30")

  // Test
  testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
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

// ── Maven publication ─────────────────────────────────────────────────────────
// Jar + POM (+ sources/javadoc jars, signed) to Maven Central. Consumed by Android projects as an
// androidTestImplementation dependency. Coordinates and POM come from gradle.properties.

mavenPublishing {
  publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
  // Sign only when a key is configured, so publishToMavenLocal works without one.
  if (providers.gradleProperty("signingInMemoryKey").isPresent) signAllPublications()
}

// Without android.jar compileJava is skipped, which would publish an empty jar. Fail instead.
val hasAndroidJar = androidJar != null

tasks
  .matching { it.name.startsWith("publish") }
  .configureEach {
    doFirst {
      if (!hasAndroidJar) {
        throw GradleException(
          "orbit-client: cannot publish without android.jar. Set ANDROID_HOME (or " +
            "ANDROID_SDK_ROOT) to an SDK with a platforms/android-* dir."
        )
      }
    }
  }
