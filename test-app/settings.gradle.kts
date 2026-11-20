rootProject.name = "orbit-test-app"

include(":composeApp")

// By default the Orbit plugin, orbit-runner and the clients come from source (../). Pass
// -PorbitPublished=true to use the released ones from Maven Central instead, the way a real
// project does.
pluginManagement {
  val published = providers.gradleProperty("orbitPublished").orNull == "true"
  if (published) {
    plugins { id("io.github.rajvinodh.orbit") version "1.0.1" }
  } else {
    includeBuild("..")
  }
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }
}

// Source mode: substitutes orbit-runner / orbit-client / orbit-client-android with ../ projects.
if (providers.gradleProperty("orbitPublished").orNull != "true") {
  includeBuild("..")
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
  }
}
