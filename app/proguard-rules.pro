# Add project specific ProGuard rules here.
# See http://developer.android.com/tools/help/proguard.html

# ============================================================
# 0. 기본 공통 설정
# ============================================================
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes EnclosingMethod

# 스택 트레이스 가독성 유지
-renamesourcefileattribute SourceFile

# ============================================================
# 1. Kotlin
# ============================================================
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Lazy {
    <fields>;
}

# Kotlin Coroutines
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# ============================================================
# 2. Jetpack Compose (Kotlin Compiler Plugin 생성 코드)
# ============================================================
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Compose Material Icons (확장 아이콘 포함)
-keep class androidx.compose.material.icons.** { *; }

# Compose runtime — Stability 분석에 필요
-keepclassmembers @androidx.compose.runtime.Stable class * {
    public *;
}
-keepclassmembers @androidx.compose.runtime.Immutable class * {
    public *;
}

# ============================================================
# 3. AndroidX Lifecycle / ViewModel
# ============================================================
-keep class androidx.lifecycle.** { *; }
-dontwarn androidx.lifecycle.**

# ============================================================
# 4. Room (KSP 생성 코드 + 앱 엔티티/DAO 보호)
# ============================================================
# Room 생성 클래스 (앱 네임스페이스 한정)
-keep class com.grepiu.aidiary.** extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep @androidx.room.Database class * { *; }

# TypeConverter 보호
-keepclassmembers class * {
    @androidx.room.TypeConverter <methods>;
}

-dontwarn androidx.room.**

# ============================================================
# 5. 앱 자체 데이터 모델 (Serialization / Gson 역직렬화 대상)
# ============================================================
# Room 엔티티 & 데이터 클래스
-keep class com.grepiu.aidiary.data.repository.DiaryEntity { *; }
-keep class com.grepiu.aidiary.data.repository.BlockEntity { *; }
-keep class com.grepiu.aidiary.data.repository.BackupData { *; }

# 백업 직렬화 대상 — Gson reflective 역직렬화
-keepclassmembers class com.grepiu.aidiary.data.repository.** {
    <fields>;
    <init>(...);
}

# ContentBlock sealed class & 모든 하위 타입 (JSON 직렬화 대상)
-keep class com.grepiu.aidiary.data.model.ContentBlock { *; }
-keep class com.grepiu.aidiary.data.model.ContentBlock$* { *; }
-keep class com.grepiu.aidiary.data.model.TitleStyle { *; }
-keep class com.grepiu.aidiary.data.model.ContentType { *; }

# MVI 상태/인텐트 클래스 (sealed class 하위 포함)
-keep class com.grepiu.aidiary.mvi.** { *; }

# ============================================================
# 6. Gson
# ============================================================
-keep class com.google.gson.** { *; }
-keep class sun.misc.Unsafe { *; }
-dontwarn sun.misc.Unsafe
-dontwarn com.google.gson.**

# Gson TypeToken 사용 시 제네릭 타입 보존
-keepattributes Signature
-keep class * implements com.google.gson.TypeAdapterFactory { *; }
-keep class * implements com.google.gson.JsonSerializer { *; }
-keep class * implements com.google.gson.JsonDeserializer { *; }

# ============================================================
# 7. OkHttp3 + Okio (모델 다운로더)
# ============================================================
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**

-keep class okio.** { *; }
-dontwarn okio.**

# OkHttp 내부 플랫폼 감지
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ============================================================
# 8. Coil (이미지 로딩)
# ============================================================
-keep class io.coil.** { *; }
-dontwarn io.coil.**
-keep class coil.** { *; }
-dontwarn coil.**

# ============================================================
# 9. Google LiteRT-LM (온디바이스 LLM)
# ============================================================
-keep class com.google.ai.edge.** { *; }
-keepclassmembers class com.google.ai.edge.** {
    native <methods>;
    <fields>;
    <init>(...);
}
-dontwarn com.google.ai.edge.**

# ============================================================
# 10. Sherpa-Onnx (음성인식, JNI Native Bridge)
# ============================================================
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keepclassmembers class com.k2fsa.sherpa.onnx.** {
    native <methods>;
    <fields>;
    <init>(...);
}
-dontwarn com.k2fsa.sherpa.onnx.**

# ============================================================
# 11. Apache Commons Compress (tar/bz2 모델 압축 해제)
# ============================================================
-keep class org.apache.commons.compress.** { *; }
-dontwarn org.apache.commons.compress.**
-dontwarn org.tukaani.xz.**

# ============================================================
# 12. Firebase Analytics
# ============================================================
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Firebase measurement / analytics 내부 직렬화
-keepclassmembers class * {
    @com.google.android.gms.common.annotation.KeepForSdk <methods>;
}

# ============================================================
# 13. Google AdMob (play-services-ads)
# ============================================================
# AdMob 공식 권장 규칙 (https://developers.google.com/admob/android/privacy/play-data-disclosure)
-keep public class com.google.android.gms.ads.** { public *; }
-keep public class com.google.ads.** { public *; }
-dontwarn com.google.android.gms.ads.**

# Native Ad View 보호 (AdMobNativeAd.kt)
-keep class com.google.android.gms.ads.nativead.NativeAdView { *; }
-keep class com.google.android.gms.ads.nativead.NativeAd { *; }

# ============================================================
# 14. AndroidX ExifInterface
# ============================================================
-keep class androidx.exifinterface.** { *; }
-dontwarn androidx.exifinterface.**

# ============================================================
# 15. JNI / Native 라이브러리 공통
# ============================================================
# JNI로 호출되는 모든 native 메서드 보호
-keepclasseswithmembernames class * {
    native <methods>;
}

# ============================================================
# 16. Android 기본 컴포넌트 (Activity / Service / BroadcastReceiver)
# ============================================================
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.preference.Preference

# Parcelable (번들 전달용)
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# Serializable
-keepclassmembers class * implements java.io.Serializable {
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ============================================================
# 17. 앱 패키지 — 전체 공개 API 보호 (리플렉션 대상 최소화)
# ============================================================
# ViewModel은 이름 변경되면 SavedStateHandle 복원 실패 가능
-keep class com.grepiu.aidiary.mvi.viewmodel.** extends androidx.lifecycle.ViewModel { *; }

# SLM 엔진 (native bridge 포함)
-keep class com.grepiu.aidiary.data.slm.SherpaEngine { *; }
-keep class com.grepiu.aidiary.data.slm.DiaryLLMEngine { *; }

# 분석 매니저 (Firebase event 이름 리플렉션 없이 사용하지만 안전하게 보호)
-keep class com.grepiu.aidiary.analytics.** { *; }
