plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.grepiu.aidiary"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.grepiu.aidiary"
        minSdk = 34
        targetSdk = 36
        versionCode = 3
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("debug") {
            manifestPlaceholders["ADMOB_APP_ID"] = "ca-app-pub-3940256099942544~3347511713"
            buildConfigField("String", "ADMOB_BANNER_ID", "\"ca-app-pub-3940256099942544/6300978111\"")
            buildConfigField("String", "ADMOB_NATIVE_ID", "\"ca-app-pub-3940256099942544/2247696110\"")
        }
        getByName("release") {
            manifestPlaceholders["ADMOB_APP_ID"] = "ca-app-pub-2803985305864806~3228866354"
            buildConfigField("String", "ADMOB_BANNER_ID", "\"ca-app-pub-2803985305864806/3228866354\"")
            buildConfigField("String", "ADMOB_NATIVE_ID", "\"ca-app-pub-2803985305864806/3228866354\"")
            isMinifyEnabled = true      // R8 코드 축소 + 난독화 활성화
            isShrinkResources = true    // 미사용 리소스 자동 제거
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // Android framework 메서드 (Log, ExifInterface 등) 가 mock 되지 않은 unit test 환경에서
    // RuntimeException 을 던지지 않도록 기본값 반환 허용
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    sourceSets {
        getByName("main") {
            jniLibs.directories.add("libs/jniLibs")
        }
    }
    androidResources {
        noCompress += listOf("gguf", "bin", "task", "litertlm", "onnx", "txt")
    }
}

dependencies {
    // Kotlin Coroutines (LiteRT-LM과 호환되는 최신 버전)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.runtime)
    implementation(libs.androidx.scenecore)
    implementation(libs.extensions1.xr)
    
    // On-Device LLM & Download support
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.ai.edge.litertlm:litertlm-android:latest.release")

    // Coil (이미지 로딩)
    implementation(libs.coil.compose)

    // ExifInterface (3D 사진/영상 포맷 자동 감지)
    implementation(libs.androidx.exifinterface)

    // Room (메인 저장소) - 2만건 이상 확장성 대응
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Sherpa-Onnx (온디바이스 음성인식) - libs/jniLibs/에 .so 파일 복사 필요
    implementation("org.apache.commons:commons-compress:1.27.1")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.12.0"))
    implementation("com.google.firebase:firebase-analytics")

    // Google AdMob (Mobile Ads SDK)
    implementation(libs.play.services.ads)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
