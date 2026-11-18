rootProject.name = "orbit-test-app"

include(":composeApp")

// Pulls in :orbit-client as a composite build so it is substituted
// for the io.github.rajvinodh:orbit-client dependency at build time.
includeBuild("..")

pluginManagement {
  // Provides the io.github.rajvinodh.orbit Gradle plugin from source. Real projects get it from
  // a repository instead (mavenLocal() / a remote repo); see orbit-gradle-plugin/README.md.
  includeBuild("..")
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
  }
}
