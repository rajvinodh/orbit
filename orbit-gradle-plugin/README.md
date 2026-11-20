# Orbit Gradle plugin

Runs your Android instrumentation tests on every connected device with Orbit, straight from your
app's build. There is no executor repo to clone.

You get two tasks:

| Task | What it does |
|---|---|
| `orbit<Variant>AndroidTest` (e.g. `orbitDebugAndroidTest`) | Builds the app APK and the test APK for that variant, then runs them. Packages and runner come from the variant. |
| `orbitTest` | Runs **prebuilt** APKs you point it at. Compiles nothing. Use it on CI when the APKs come from an earlier job. |

## 1. Add the plugin

`settings.gradle.kts`
```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()        // the Orbit plugin is published here
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()        // the plugin resolves io.github.rajvinodh:orbit-runner from here
    }
}
```

`app/build.gradle.kts`
```kotlin
plugins {
    id("com.android.application")
    id("io.github.rajvinodh.orbit") version "1.0.1"
}

apply(from = "orbit.gradle")   // your team's fixed Orbit config

dependencies {
    // Device-side client (pulls in io.github.rajvinodh:orbit-client)
    androidTestImplementation("io.github.rajvinodh:orbit-client-android:1.0.1")
}
```

To try an unreleased build, run
`./gradlew :orbit-runner:publishToMavenLocal :orbit-client:publishToMavenLocal :orbit-client-android:publishToMavenLocal :orbit-gradle-plugin:publishToMavenLocal` from the orbit
repo and add `mavenLocal()` first in both repository blocks.

## 2. Keep your fixed config in `orbit.gradle` (in your project)

```groovy
orbit {
    grantAll = true
    trackLogcat = true
    logcatLevel = "I"
    executionMode = "DISTRIBUTE"        // or "FANOUT": every test on every device
    instrumentationArgs = [listener: "com.myapp.AnnotationsTestPrinter"]
    ignoreFailures = false

    // Any Groovy logic is fine, e.g. map a short -P flag to annotation filters:
    if (project.hasProperty("TestGroup")) {
        instrumentationArgs.put("annotation", "com.myapp.annotations." + project.TestGroup)
        title = project.TestGroup
    }
}
```

See [`test-app/composeApp/orbit.gradle`](../test-app/composeApp/orbit.gradle) for a working example.

## 3. Run

```bash
# Build + run the debug variant
./gradlew :app:orbitDebugAndroidTest

# Narrow it down / change behaviour for this run only
./gradlew :app:orbitDebugAndroidTest -Porbit.className=com.myapp.LoginTest -Porbit.methodName=validLogin
./gradlew :app:orbitDebugAndroidTest -Porbit.devices=emulator-5554,emulator-5556 -Porbit.executionMode=FANOUT

# Prebuilt APKs, no compilation (packages and runner are read from the test APK)
./gradlew :app:orbitTest -Porbit.testApk=/path/app-debug-androidTest.apk -Porbit.targetApk=/path/app-debug.apk
```

The report is written to `build/orbit-output/orbit-report/index.html`, and the task prints a clickable link.

## Options

Every option can be set in `orbit { }` and overridden for one run with `-Porbit.<name>=<value>`.
**`-P` wins over `orbit { }`.** The exception is `instrumentationArgs`, where the `-P` pairs are *merged*
on top of the fixed ones.

| Name | Type | Notes |
|---|---|---|
| `testApk`, `targetApk` | file | `orbitTest` only. Relative `-P` paths resolve against the root project dir |
| `otherApks` | files | extra APKs to install (`orbit { }` only) |
| `appPackage`, `testPackage`, `testRunner` | string | `orbitTest` only; read from the test APK if unset |
| `devices`, `skipDevices` | list | serials; `-P` form is comma-separated |
| `allowNoDevices` | boolean | |
| `adbTimeout` | string | ISO-8601, e.g. `PT10M` |
| `availabilityTimeoutMin` | long | |
| `className`, `methodName` | string | `methodName` requires `className` |
| `instrumentationArgs` | map | `-Porbit.instrumentationArgs=k=v,k2=v2` |
| `externalIds` | string | comma-separated, or `@/path/ids.txt` |
| `executionMode` | string | `DISTRIBUTE` (default) / `FANOUT` |
| `grantAll`, `clearAppData`, `coverage` | boolean | |
| `trackLogcat`, `recordVideo`, `trackNetworkLogs` | boolean | |
| `logcatLevel` | string | `V` `D` `I` `W` `E` |
| `outputDir` | dir | default `build/orbit-output` |
| `title` | string | report title |
| `appiumSettingsVersion`, `noAppiumSettings` | string / boolean | |
| `debug` | boolean | verbose runner logging |
| `ignoreFailures` | boolean | default `false`; when `true`, test failures do not fail the build |
| `runnerVersion` | string | orbit-runner version; defaults to the plugin version |
| `sdkDir` | dir | default: AGP's SDK (variant tasks), else `ANDROID_HOME` |
| `workerMaxHeap` | string | heap of the Orbit runner JVM, default `1g` (`orbit { }` only) |

## How it works

Each run starts a fresh JVM running the orbit-runner CLI (`java … OrbitCommandKt <args>`), with a
classpath of just orbit-runner and its dependencies (configuration `orbitRunner`). Its ddmlib and
logging never conflict with the copies AGP and Gradle bring into your build, and the runner's
progress output streams to your console. To pin a different runner version than the plugin's own:

```groovy
orbit { runnerVersion = "1.0.1" }
```
