# Orbit — Command Reference

All commands run from the repo root unless noted otherwise. Use `./gradlew` (the wrapper) — don't rely on a global `gradle` install.

---

## 1. Build

```bash
./gradlew build                 # compile + test everything
./gradlew :orbit-runner:shadowJar   # fat JAR of the CLI (orbit-runner/build/libs/orbit-runner-1.0.0.jar)
./gradlew :orbit-runner:run --args="<test.apk> --serial emulator-5554"   # run the CLI directly via Gradle
```

`orbit-runner`'s `processResources` task depends on `buildFrontend`, which runs `npm run build` in `orbit-report/` and copies `dist/{index.html,main.js,main.css}` into the JAR. So building `orbit-runner` requires `orbit-report`'s npm deps to be installed first (`npm ci` in `orbit-report/`, see §5).

### Gradle plugin (`orbit-gradle-plugin`)

```bash
# Publish runner + plugin to ~/.m2 so any local Android project can use them
./gradlew :orbit-runner:publishToMavenLocal :orbit-gradle-plugin:publishToMavenLocal

./gradlew :orbit-gradle-plugin:test            # unit + TestKit tests (no device)
./gradlew :orbit-gradle-plugin:validatePlugins # task annotation checks

# Dogfood in test-app (plugin comes from source via pluginManagement.includeBuild(".."))
cd test-app
./gradlew :composeApp:orbitDebugAndroidTest                                   # builds both APKs, then runs
./gradlew :composeApp:orbitDebugAndroidTest -Porbit.className=<fqcn> -Porbit.executionMode=FANOUT
./gradlew :composeApp:orbitTest -Porbit.testApk=<test.apk> -Porbit.targetApk=<app.apk>   # prebuilt, no compile
```

Integration guide for app teams: [`orbit-gradle-plugin/README.md`](orbit-gradle-plugin/README.md).

---

## 2. Unit tests

```bash
./gradlew test                          # all modules
./gradlew :orbit-runner:test            # orbit-runner only
./gradlew :orbit-client:test            # orbit-client only
```

Reports:
- HTML: `orbit-runner/build/reports/tests/test/index.html`, `orbit-client/build/reports/tests/test/index.html`
- XML (JUnit format): `orbit-runner/build/test-results/test/`, `orbit-client/build/test-results/test/`

Run a single test class or method:

```bash
./gradlew :orbit-runner:test --tests "io.github.rajvinodh.orbit.execution.TestExecutionPipelineTest"
./gradlew :orbit-runner:test --tests "*.TestExecutionPipelineTest.attemptNumber incremented across retries"
```

`orbit-client` requires `ANDROID_HOME` (or `ANDROID_SDK_ROOT`) pointing at an SDK with a `platforms/android-*` dir — `compileJava` is silently skipped otherwise (logged as a warning), which also skips its tests.

---

## 3. Integration tests

Separate Gradle source set (`orbit-runner/src/integrationTest/`), no device/emulator required — drives orchestration via a scripted `IDevice` fake.

```bash
./gradlew :orbit-runner:integrationTest
```

Report: `orbit-runner/build/reports/tests/integrationTest/index.html`

---

## 4. E2E tests

Runs the real CLI against a live AVD, using `test-app` as the target. Requires `ANDROID_HOME` set and a running/connected emulator or device.

```bash
# 1. Build the test app's debug + androidTest APKs
cd test-app
./gradlew :composeApp:assembleDebug :composeApp:assembleDebugAndroidTest
cd ..

# 2. Run the e2e suite (drives orbit-runner's OrbitRunner against the AVD)
./gradlew :orbit-runner:e2eTest
```

- Always re-runs (`outputs.upToDateWhen { false }`) since results depend on live device state.
- Test-run output goes to `orbit-runner/build/e2e-output/` (including `orbit-report/`), path exposed to the test via the `orbit.e2e.outputDir` system property.
- `testLogging` is set to stream stdout live, so `--info`/`-i` isn't needed to watch progress.
- Mirrors `.github/workflows/e2e.yml`, which additionally installs frontend deps (`npm ci` in `orbit-report/`) and enables KVM before invoking `android-emulator-runner`.

---

## 5. Coverage reports

### 5a. Unit test coverage (orbit-runner / orbit-client's own code)

Both modules apply the JaCoCo Gradle plugin. `test` is `finalizedBy` the report task, so a plain `./gradlew test` also regenerates the coverage report:

```bash
./gradlew :orbit-runner:test          # also runs jacocoTestReport
./gradlew :orbit-client:test          # also runs jacocoTestReport

# or explicitly, without re-running tests if already up to date:
./gradlew :orbit-runner:jacocoTestReport
./gradlew :orbit-client:jacocoTestReport
```

Report output:
- HTML: `orbit-runner/build/reports/jacoco/test/html/index.html`, `orbit-client/build/reports/jacoco/test/html/index.html`
- XML: `orbit-runner/build/reports/jacoco/test/jacocoTestReport.xml`, `orbit-client/build/reports/jacoco/test/jacocoTestReport.xml`

### 5b. Coverage of the app-under-test (collected by Orbit)

This is Orbit's own coverage-collection *feature* — JaCoCo `.ec` data pulled from devices during an Orbit run against `test-app`, merged, then rendered into an HTML/XML report. Unrelated to §5a above.

```bash
# After an Orbit run (e.g. the e2eTest above, or a manual `orbit` invocation
# with --coverage) has produced orbit-runner/build/e2e-output/coverage/merged-coverage.ec:
cd test-app
./gradlew :composeApp:orbitCoverageReport
```

Optional override if the coverage data lives elsewhere:

```bash
./gradlew :composeApp:orbitCoverageReport -PorbitOutput=/path/to/orbit-output
```

Report output:
- HTML: `test-app/composeApp/build/coverage-report/html/index.html`
- XML: `test-app/composeApp/build/coverage-report/coverage.xml`

---

## 6. Frontend (orbit-report)

```bash
cd orbit-report
npm ci             # install deps (matches CI; use over `npm install` for reproducibility)
npm run dev        # Vite dev server
npm run build      # tsc -b && vite build → dist/
npm run lint       # eslint .
npm run preview    # preview the built dist/
```

---

## 7. Formatting / lint (Spotless — Java + Kotlin + Gradle Kotlin DSL)

```bash
./gradlew spotlessCheck   # verify formatting, fails if anything is unformatted
./gradlew spotlessApply   # auto-format in place
```

Covers `orbit-runner`, `orbit-client`, `orbit-client-android` (`*.java`, `*.kt` via ktfmt/Google Java Format) and all `*.gradle.kts` files.

---

## 8. CI reference (what actually runs in GitHub Actions)

`.github/workflows/ci.yml` (on push to `main`/`release/**` and on PRs):
```bash
npm ci                     # (in orbit-report/)
./gradlew test
./gradlew integrationTest
./gradlew shadowJar
```

`.github/workflows/e2e.yml` (manual dispatch + weekly Monday 04:00 UTC cron):
```bash
npm ci                     # (in orbit-report/)
./gradlew :composeApp:assembleDebug :composeApp:assembleDebugAndroidTest   # (in test-app/)
./gradlew :orbit-runner:e2eTest                                            # on a spun-up AVD (API 30, x86_64)
```

Neither workflow currently runs `spotlessCheck`.