// Macrobenchmark module — the only quantitative gate for "jank-free" startup
// and scrolling. It is a com.android.test module: it contains no app code, it
// builds a *separate* APK that drives the real :app APK (built as the
// `benchmark` build type, see app/build.gradle.kts).
//
// Run on a real device with:
//   .\gradlew.bat :macrobenchmark:connectedBenchmarkAndroidTest
plugins {
    alias(libs.plugins.android.test)
    // AGP 9 ships built-in Kotlin support for com.android.test modules, so the
    // standalone kotlin-android plugin is deliberately NOT applied here (same
    // reason as :app).
}

android {
    namespace = "com.tailg.plus.macrobenchmark"
    compileSdk = 37

    // The module under test. `benchmark` is a release-like (R8-minified,
    // non-debuggable, profileable) build type of :app signed with the debug key,
    // so the measured numbers reflect shipping code (see app/build.gradle.kts).
    targetProjectPath = ":app"
    // No `testBuildType`: AGP 9's com.android.test DSL dropped it. The tested APK
    // is now picked by build-type attribute matching, so naming the test module's
    // own build type `benchmark` (below) is what makes
    // :macrobenchmark:connectedBenchmarkAndroidTest drive :app:benchmark.

    defaultConfig {
        minSdk = 26
        // Macrobenchmark drives the app through Instrumentation + UiAutomator.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // R8 keep / -dontwarn rules live in `src/main/keepRules/*.keep`, NOT in a
    // module-root proguard-rules.pro: that is the only location AGP 9 consults
    // for this module (its R8 task probes exactly that folder, and it rejects
    // any other extension with "Use .keep extensions for keepRules source
    // folders"). `proguardFiles { }` here is silently ignored for
    // com.android.test modules.
    buildTypes {
        // com.android.test creates only `debug` by default. The variant name has
        // to match :app's release-like build type so attribute matching picks
        // :app:benchmark as the tested APK.
        //
        // `isDebuggable = true` is deliberate and matches the AndroidX
        // macrobenchmark template: this flag describes the *test* APK, and an
        // instrumentation APK must be debuggable for the runner to attach to
        // it. The profiler downgrade that would invalidate the numbers is
        // controlled by the TESTED app (its `benchmark` build type in
        // app/build.gradle.kts keeps isDebuggable = false + isProfileable).
        create("benchmark") {
            isDebuggable = true
            matchingFallbacks += listOf("release")
            // The tested APK (:app:benchmark) is R8-minified. AGP refuses to run
            // the macrobenchmark variant unless the *test* project is shrunk the
            // same way — `checkTestedAppObfuscationBenchmark` fails with
            // "Mapping file found in tested application" otherwise, because a
            // test APK that is not obfuscated cannot drive a target that is.
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    // NOTE: no lint { abortOnError = true } here on purpose — :app already runs
    // strict lint; this module is a measurement harness, not shipped code.
}
dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.ext.junit4)
    implementation(libs.androidx.test.uiautomator)
}
