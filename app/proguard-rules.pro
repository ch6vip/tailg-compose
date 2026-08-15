# Keep Moshi generated adapters
-keepclassmembers class **$$JsonAdapter {
    public static <methods>;
}
-keepnames @com.squareup.moshi.JsonClass class *

# Keep Paho MQTT classes
-keep class org.eclipse.paho.** { *; }
-dontwarn org.eclipse.paho.**

# Keep Lottie
-dontwarn com.airbnb.lottie.**
