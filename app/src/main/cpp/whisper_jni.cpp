#include <jni.h>
#include <string>
#include <vector>
#include <fstream>
#include <cstring>
#include <android/log.h>
#include "whisper.h"

#define TAG "WhisperJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// WAV 파일에서 PCM float 샘플을 읽어오는 헬퍼
static std::vector<float> read_wav(const char* path) {
    std::ifstream file(path, std::ios::binary);
    if (!file) return {};

    // WAV 헤더 파싱
    char header[44];
    file.read(header, 44);
    if (std::strncmp(header, "RIFF", 4) != 0 || std::strncmp(header + 8, "WAVE", 4) != 0) {
        return {};
    }

    int bitsPerSample = *(short*)(header + 34);
    int dataSize = *(int*)(header + 40);

    std::vector<float> samples;
    if (bitsPerSample == 16) {
        std::vector<short> pcm(dataSize / 2);
        file.read((char*)pcm.data(), dataSize);
        samples.reserve(pcm.size());
        for (short s : pcm) {
            samples.push_back(s / 32768.0f);
        }
    } else if (bitsPerSample == 8) {
        std::vector<char> pcm(dataSize);
        file.read(pcm.data(), dataSize);
        samples.reserve(pcm.size());
        for (char s : pcm) {
            samples.push_back(s / 128.0f);
        }
    }
    return samples;
}

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

    // WAV 파일에서 float 샘플 읽기 (신규 API: whisper_full이 파일 경로 대신 샘플을 직접 받음)
    std::vector<float> samples = read_wav(wavPath);
    if (samples.empty()) {
        LOGE("Failed to read WAV file: %s", wavPath);
        env->ReleaseStringUTFChars(wavPathJ, wavPath);
        env->ReleaseStringUTFChars(languageJ, lang);
        return env->NewStringUTF("");
    }

    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_progress = false;
    params.print_special = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.language = lang;
    params.n_threads = 4;
    params.offset_ms = 0;
    params.no_context = true;
    params.single_segment = true;

    LOGD("Transcribing: %s (lang=%s, samples=%zu)", wavPath, lang, samples.size());

    if (whisper_full(ctx, params, samples.data(), static_cast<int>(samples.size())) != 0) {
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
