plugins {
    // AGP 9 ships built-in Kotlin support — the separate kotlin-android
    // plugin must NOT be applied (its `kotlin` extension collides with the
    // one AGP registers).
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val allowInsecureMqttTls = providers.gradleProperty("allowInsecureMqttTls")
    .orNull
    ?.toBooleanStrictOrNull()
    ?: false

android {
    namespace = "com.tailg.plus"
    // 37.0 (Android 17): required by the 2026 androidx/okhttp line
    // (AAR metadata minCompileSdk=37). targetSdk 36 satisfies the Play
    // policy window (2026-08 requires 36).
    compileSdk = 37

    // ------------------------------------------------------------------
    // Signing configuration
    // ------------------------------------------------------------------
    // The keystore is NOT in the repo. In CI it is restored from the
    // ANDROID_KEYSTORE_BASE64 secret into $RUNNER_TEMP (see .github/
    // workflows/build.yml); locally, export the four variables below.
    //
    // There is deliberately NO fallback password: a hardcoded credential
    // in a public repo means anyone can sign an APK as us. If the
    // variables are missing, the release build simply comes out unsigned
    // (still a valid R8/ProGuard smoke test) instead of failing.
    // ------------------------------------------------------------------
    val signingStoreFile = System.getenv("STORE_FILE")
        ?.takeIf { it.isNotBlank() }
        ?.let { file(it) }
    val signingStorePassword = System.getenv("KEYSTORE_PASSWORD")
    val signingKeyAlias = System.getenv("KEY_ALIAS")
    val signingKeyPassword = System.getenv("KEY_PASSWORD")

    signingConfigs {
        // Only register the config when the key is actually present, so a
        // build without it does not fail with "Keystore file not found".
        if (signingStoreFile?.exists() == true
            && !signingStorePassword.isNullOrEmpty()
            && !signingKeyAlias.isNullOrEmpty()
            && !signingKeyPassword.isNullOrEmpty()
        ) {
            create("release") {
                storeFile = signingStoreFile
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    // Resolved once, up front: PR builds do not receive the signing key.
    val releaseSigningConfig = signingConfigs.findByName("release")
    if (releaseSigningConfig == null) {
        logger.lifecycle(
            "release signing: no keystore configured (STORE_FILE/KEYSTORE_PASSWORD/" +
                "KEY_ALIAS/KEY_PASSWORD) — the release APK will be UNSIGNED."
        )
    }

    defaultConfig {
        applicationId = "com.tailg.plus"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        // Map tiles: optional Tianditu token (Gradle property `tiandituToken`),
        // mirroring the Dart TIANDITU_TOKEN dart-define; blank falls back to AutoNavi.
        // Provider API so the configuration cache stays compatible.
        buildConfigField(
            "String",
            "TIANDITU_TOKEN",
            "\"${providers.gradleProperty("tiandituToken").orNull?.replace("\"", "") ?: ""}\"",
        )
        buildConfigField("boolean", "ALLOW_INSECURE_MQTT_TLS", "false")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Sign only when the key was restored; otherwise unsigned.
            releaseSigningConfig?.let { signingConfig = it }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            isMinifyEnabled = false
            buildConfigField(
                "boolean",
                "ALLOW_INSECURE_MQTT_TLS",
                allowInsecureMqttTls.toString(),
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // AGP 9 built-in Kotlin: kotlinc's jvmTarget is aligned with
    // compileOptions above (the standalone kotlinOptions{} block was removed
    // with the kotlin-android plugin and is gone in Kotlin 2.4).
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    lint {
        // Fail the build on lint errors. Current report is warnings-only;
        // keep abort on Error/Fatal so regressions fail CI.
        abortOnError = true
        checkReleaseBuilds = true
    }
}

// Compose compiler metrics: emit per-file recomposition counts and composable
// sizes to build/compose_compiler. Watch these in CI to catch regressions
// (a screen silently growing its recomposition scope shows up here before it
// becomes jank on device). Reports are debug-only artifacts; they do not ship.
composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_compiler")
    metricsDestination = layout.buildDirectory.dir("compose_compiler")
}

dependencies {
    // AndroidX core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // ProcessLifecycleOwner — app-level foreground/background events drive the
    // induction RSSI loop's foreground-service decisions (see TailgApplication).
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.material.kolor)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Network
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)
    ksp(libs.moshi.codegen)

    // Persistence / security
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)

    // Location
    implementation(libs.play.services.location)

    // Map (osmdroid — new dep for the map SDK pass, see UI_PORT_PLAN map TODO)
    implementation(libs.osmdroid.android)

    // QR rendering for the garage vehicle-code sheet (zxing core, pure JVM)
    implementation(libs.zxing.core)

    // Camera / scanner
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.mlkit.barcode)

    // Animation
    implementation(libs.lottie.compose)

    // MQTT
    implementation(libs.paho.mqtt)

    // Logging
    implementation(libs.timber)

    // Baseline profile — ships the startup profile in the APK and applies it
    // on first launch (see src/main/baseline-prof.txt).
    implementation(libs.androidx.profileinstaller)

    // Unit tests
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)

    // Instrumented / Compose UI tests
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
