rootProject.name = "orbit"

include(":orbit-runner")

include(":orbit-client")

include(":orbit-client-android")

include(":orbit-gradle-plugin")

pluginManagement {
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }
}
