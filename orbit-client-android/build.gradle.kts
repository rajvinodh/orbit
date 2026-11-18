plugins { id("com.android.library") }

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
  // Core orbit-client types (OrbitRule.KEY_*, annotations)
  compileOnly(project(":orbit-client"))
  // ATSL — properly resolved as AAR by the Android Gradle Plugin
  compileOnly("androidx.test:runner:1.6.2")
  compileOnly("junit:junit:4.13.2")
}
