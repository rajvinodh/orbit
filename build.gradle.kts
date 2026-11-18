plugins {
  kotlin("jvm") version "1.9.22" apply false
  kotlin("plugin.lombok") version "1.9.22" apply false
  kotlin("plugin.sam.with.receiver") version "1.9.22" apply false
  id("com.android.library") version "8.5.2" apply false
  id("com.diffplug.spotless") version "8.7.0"
  id("com.vanniktech.maven.publish") version "0.30.0" apply false
  jacoco
}

allprojects {
  // Coordinates and POM metadata live in gradle.properties (GROUP, VERSION_NAME, POM_*), which
  // the vanniktech publish plugin reads.
  group = property("GROUP").toString()
  version = property("VERSION_NAME").toString()

  repositories {
    mavenCentral()
    google()
  }
}

// ── Aggregated coverage report (orbit-runner + orbit-client) ──────────────────

val jacocoReportProjects = listOf(project(":orbit-runner"), project(":orbit-client"))

tasks.register<JacocoReport>("jacocoRootReport") {
  group = "verification"
  description = "Generates an aggregated JaCoco coverage report for orbit-runner and orbit-client"

  dependsOn(jacocoReportProjects.map { it.tasks.named("test") })

  val sourceDirs =
    jacocoReportProjects.map { it.the<SourceSetContainer>()["main"].allSource.srcDirs }
  additionalSourceDirs.setFrom(sourceDirs)
  sourceDirectories.setFrom(sourceDirs)
  classDirectories.setFrom(jacocoReportProjects.map { it.the<SourceSetContainer>()["main"].output })
  executionData.setFrom(
    jacocoReportProjects.map { it.layout.buildDirectory.file("jacoco/test.exec") }
  )

  reports {
    html.required.set(true)
    xml.required.set(true)
    html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/aggregate/html"))
    xml.outputLocation.set(
      layout.buildDirectory.file("reports/jacoco/aggregate/jacocoAggregateReport.xml")
    )
  }
}

spotless {
  java {
    target(
      "orbit-runner/src/**/*.java",
      "orbit-client/src/**/*.java",
      "orbit-client-android/src/**/*.java",
    )
    googleJavaFormat("1.22.0")
    removeUnusedImports()
    trimTrailingWhitespace()
    endWithNewline()
  }
  kotlin {
    target("orbit-runner/src/**/*.kt", "orbit-gradle-plugin/src/**/*.kt")
    ktfmt("0.51").googleStyle()
    trimTrailingWhitespace()
    endWithNewline()
  }
  kotlinGradle {
    target("**/*.gradle.kts")
    ktfmt("0.51").googleStyle()
    trimTrailingWhitespace()
    endWithNewline()
  }
}
