# Orbit

Runs Android instrumentation tests on every connected device at once and produces an HTML report.

- **DISTRIBUTE** (default): tests are split across devices.
- **FANOUT**: every test runs on every device.

Optional extras: logcat capture, screen recording, network logs, and coverage.

## Use it from Gradle

```kotlin
// settings.gradle.kts: mavenCentral() in pluginManagement and dependencyResolutionManagement
plugins {
    id("com.android.application")
    id("io.github.rajvinodh.orbit") version "1.0.1"
}
```

```bash
./gradlew :app:orbitDebugAndroidTest                      # builds both APKs, then runs the tests
./gradlew :app:orbitTest -Porbit.testApk=test.apk -Porbit.targetApk=app.apk   # prebuilt APKs, no compile
```

Setup and all the options are in [orbit-gradle-plugin/README.md](orbit-gradle-plugin/README.md).

## Use it from the CLI

```bash
./gradlew :orbit-runner:shadowJar
java -jar orbit-runner/build/libs/orbit-runner-1.0.1.jar app-debug-androidTest.apk app-debug.apk
```

## Modules

| Module | What it is |
|---|---|
| `orbit-runner` | Core runner and CLI |
| `orbit-gradle-plugin` | Gradle plugin `io.github.rajvinodh.orbit` |
| `orbit-report` | Report UI (React), bundled into the runner |
| `orbit-client`, `orbit-client-android` | Clients used on the device side |
| `test-app` | Sample app used to test Orbit against itself |

## Docs

- [orbit-design.md](orbit-design.md): architecture
- [CONTRIBUTION_GUIDE.md](CONTRIBUTION_GUIDE.md): how to contribute
- [COMMANDS.md](COMMANDS.md): build, test and release commands

Licensed under [Apache 2.0](LICENSE).
