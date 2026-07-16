# Add project specific ProGuard rules here.

# LiteRT-LM SDK
-keep class com.google.ai.edge.** { *; }
-dontwarn com.google.ai.edge.**

# OkHttp
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**

# Okio
-keep class okio.** { *; }
-dontwarn okio.**

# Kotlin coroutines
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**
