#include <jni.h>
#include <string>
#include <android/log.h>
#include "whisper.h"

#define TAG "WhisperJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_grepiu_aidiary_data_slm_WhisperEngine_nativeInit(
    JNIEnv* env, jclass, jstring modelPathJ) {
    
    const char* modelPath = env->GetStringUTFChars(modelPathJ, nullptr);
    
    struct whisper_context_params cparams = whisper_context_default_params();
    struct whisper_context* ctx = whisper_init_from_file_with_params(modelPath, cparams);
    
    env->ReleaseStringUTFChars(modelPathJ, modelPath);
    
    if (!ctx) {
        LOGE("Failed to initialize whisper context from: %s", modelPath);
        return 0;
    }
    LOGD("Whisper context initialized, ptr=%p", ctx);
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jstring JNICALL
Java_com_grepiu_aidiary_data_slm_WhisperEngine_nativeTranscribe(
    JNIEnv* env, jclass, jlong ptr, jstring wavPathJ, jstring languageJ) {
    
    auto* ctx = reinterpret_cast<struct whisper_context*>(ptr);
    if (!ctx) return env->NewStringUTF("");
    
    const char* wavPath = env->GetStringUTFChars(wavPathJ, nullptr);
    const char* lang = env->GetStringUTFChars(languageJ, nullptr);
    
    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_progress = false;
    params.print_special = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.translate = false;
    params.language = lang;
    params.n_threads = 4;
    params.offset_ms = 0;
    params.no_context = true;
    params.single_segment = true;
    
    LOGD("Transcribing: %s (lang=%s)", wavPath, lang);
    
    if (whisper_full(ctx, params, wavPath, 0) != 0) {
        LOGE("whisper_full failed");
        env->ReleaseStringUTFChars(wavPathJ, wavPath);
        env->ReleaseStringUTFChars(languageJ, lang);
        return env->NewStringUTF("");
    }
    
    const int n_segments = whisper_full_n_segments(ctx);
    std::string result;
    for (int i = 0; i < n_segments; i++) {
        const char* text = whisper_full_get_segment_text(ctx, i);
        if (text && strlen(text) > 0) {
            if (!result.empty()) result += " ";
            result += text;
        }
    }
    
    env->ReleaseStringUTFChars(wavPathJ, wavPath);
    env->ReleaseStringUTFChars(languageJ, lang);
    
    LOGD("Transcription result: %s", result.c_str());
    return env->NewStringUTF(result.c_str());
}

JNIEXPORT void JNICALL
Java_com_grepiu_aidiary_data_slm_WhisperEngine_nativeFree(
    JNIEnv*, jclass, jlong ptr) {
    auto* ctx = reinterpret_cast<struct whisper_context*>(ptr);
    if (ctx) {
        whisper_free(ctx);
        LOGD("Whisper context freed");
    }
}

} // extern "C"
