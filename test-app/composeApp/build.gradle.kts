@file:OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidApplication)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.composeCompiler)
  jacoco
  id("io.github.rajvinodh.orbit")
}

// Team-fixed Orbit config. Per-run values come in as -Porbit.<name>.
apply(from = "orbit.gradle")

kotlin {
  androidTarget {
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
  }

  sourceSets {
    androidMain.dependencies {
      implementation(compose.preview)
      implementation(libs.androidx.activity.compose)
    }
    commonMain.dependencies {
      implementation(compose.runtime)
      implementation(compose.foundation)
      implementation(compose.material3)
      implementation(compose.ui)
    }
  }
}

android {
  namespace = "io.github.rajvinodh.orbit.testapp"
  compileSdk = libs.versions.android.compileSdk.get().toInt()

  sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
  sourceSets["main"].res.srcDirs("src/androidMain/res")
  sourceSets["androidTest"].kotlin.srcDirs("src/androidInstrumentedTest/kotlin")

  defaultConfig {
    applicationId = "io.github.rajvinodh.orbit.testapp"
    minSdk = libs.versions.android.minSdk.get().toInt()
    targetSdk = libs.versions.android.targetSdk.get().toInt()
    versionCode = 1
    versionName = "1.0"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildTypes {
    debug { enableAndroidTestCoverage = true }
    release { isMinifyEnabled = false }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }

  dependencies {
    debugImplementation(compose.uiTooling)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(compose.uiTestJUnit4)
    androidTestImplementation("io.github.rajvinodh:orbit-client:1.0.1")
    androidTestImplementation("io.github.rajvinodh:orbit-client-android:1.0.1")
  }
}

// Accepts -PorbitOutput=<path> to override; defaults to orbit-output/ next to this project.
val orbitOutput =
  findProperty("orbitOutput")?.toString()
    ?: "${rootProject.projectDir}/../orbit-runner/build/e2e-output"

tasks.register<JacocoReport>("orbitCoverageReport") {
  group = "verification"
  description = "Generates a JaCoCo HTML/XML report from the coverage data collected by Orbit."

  print(orbitOutput)

  executionData.setFrom("$orbitOutput/coverage/merged-coverage.ec")

  classDirectories.setFrom(
    fileTree("build/tmp/kotlin-classes/debug") {
      // exclude generated Compose/R classes that inflate line counts
      exclude("**/R.class", "**/R$*.class", "**/BuildConfig.*", "**/*\$\$serializer.class")
    }
  )

  sourceDirectories.setFrom(files("src/commonMain/kotlin", "src/androidMain/kotlin"))

  reports {
    html.required.set(true)
    html.outputLocation.set(layout.buildDirectory.dir("coverage-report/html"))
    xml.required.set(true)
    xml.outputLocation.set(layout.buildDirectory.file("coverage-report/coverage.xml"))
  }
}
