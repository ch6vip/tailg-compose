plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
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
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tailg.plus"
        minSdk = 26
        targetSdk = 35
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
    kotlinOptions {
        jvmTarget = "17"
    }
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
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)

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
