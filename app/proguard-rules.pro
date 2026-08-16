# tailg-compose ProGuard/R8 rules
#
# Release builds run `isMinifyEnabled = true` + `isShrinkResources = true`
# (app/build.gradle.kts). Keep everything R8 cannot prove is reachable:
# reflection-driven wire DTOs (Moshi @JsonClass adapters are generated at
# compile time via KSP, but the DTO classes themselves are looked up only by
# the generated adapter classes), Retrofit service interfaces, and any
# annotation-processed model types.

# ---- Moshi wire DTOs (data.model + data.cloud + data.mqtt payloads) ----
# Generated Moshi adapters reference DTO fields directly, so keeping the
# adapter classes and their referenced types is what matters. Belt-and-braces:
# keep all @JsonClass-annotated types and their constructors/fields.
-keep @com.squareup.moshi.JsonClass class * { *; }
-keepclassmembers class **$$JsonAdapter {
    public static <methods>;
}
-keepnames @com.squareup.moshi.JsonClass class *
-dontwarn com.squareup.moshi.**

# ---- Retrofit ----
# Retrofit service interfaces are only referenced via reflection by the
# generated proxy; keep all methods and the interface.
-keep,allowobfuscation,allowshrinking interface com.tailg.plus.data.cloud.OfficialCloudApiService
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**

# ---- Hilt / Dagger ----
# Hilt generated components are looked up via reflection at runtime.
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keepclassmembers,allowobfuscation class * {
    @dagger.hilt.android.lifecycle.HiltViewModel *;
}
-dontwarn dagger.hilt.**

# ---- osmdroid ----
# osmdroid uses reflection to instantiate tile sources / renderers.
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# ---- Paho MQTT ----
-keep class org.eclipse.paho.** { *; }
-dontwarn org.eclipse.paho.**

# ---- Lottie ----
-dontwarn com.airbnb.lottie.**

# ---- CameraX / ML Kit / Play services ----
-dontwarn androidx.camera.**
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.**
-dontwarn com.google.firebase.**

# ---- Kotlin coroutines / Timber ----
-dontwarn kotlinx.coroutines.**
-dontwarn timber.log.**
