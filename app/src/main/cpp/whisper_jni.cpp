#include <jni.h>
#include <string>
#include <vector>
#include <fstream>
#include <cstring>
#include <cstdio>
#include <cstdlib>
#include <android/log.h>
#include "whisper.h"

#define TAG "WhisperJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// ---- 콜백 (디버깅용) ----

static void progress_cb(struct whisper_context*, struct whisper_state*, int progress, void*) {
    LOGD("██ Progress: %d%%", progress);
}

static void segment_cb(struct whisper_context* ctx, struct whisper_state*, int n_new, void*) {
    int total = whisper_full_n_segments(ctx);
    LOGD("██ New segments: +%d (total=%d)", n_new, total);
}

static std::vector<float> read_wav(const char* path) {
    std::ifstream file(path, std::ios::binary);
    if (!file) { LOGE("Cannot open: %s", path); return {}; }
    char header[44];
    file.read(header, 44);
    if (std::strncmp(header, "RIFF", 4) || std::strncmp(header + 8, "WAVE", 4)) { LOGE("Bad WAV header"); return {}; }
    int bits = *(short*)(header + 34);
    int dataSize = *(int*)(header + 40);
    LOGD("WAV: bits=%d, data=%d bytes", bits, dataSize);
    std::vector<float> samples;
    if (bits == 16) {
        std::vector<short> pcm(dataSize / 2);
        file.read((char*)pcm.data(), dataSize);
        samples.reserve(pcm.size());
        for (short s : pcm) samples.push_back(s / 32768.0f);
    }
    LOGD("Loaded %zu float samples (%.1fs @ 16kHz)", samples.size(), (float)samples.size() / 16000.0f);
    return samples;
}

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_grepiu_aidiary_data_slm_WhisperEngine_nativeInit(JNIEnv* env, jclass, jstring pathJ) {
    const char* path = env->GetStringUTFChars(pathJ, nullptr);
    
    // 모델 파일 크기 확인
    FILE* f = fopen(path, "rb");
    if (f) {
        fseek(f, 0, SEEK_END);
        long size = ftell(f);
        fclose(f);
        LOGD("Model file: %s (%ld MB)", path, size / 1024 / 1024);
    }
    
    // NUMA 비활성화 (Android에서 문제 유발 가능)
    setenv("GGML_NUMA", "0", 1);
    
    // 단순 init (기본 파라미터)
    struct whisper_context_params cparams = whisper_context_default_params();
    struct whisper_context* ctx = whisper_init_from_file_with_params(path, cparams);
    env->ReleaseStringUTFChars(pathJ, path);
    LOGD("Model loaded: %p (using whisper_init_from_file)", ctx);
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jstring JNICALL
Java_com_grepiu_aidiary_data_slm_WhisperEngine_nativeTranscribe(JNIEnv* env, jclass, jlong ptr, jstring wavJ, jstring langJ) {
    auto* ctx = reinterpret_cast<struct whisper_context*>(ptr);
    if (!ctx) return env->NewStringUTF("");

    const char* wavPath = env->GetStringUTFChars(wavJ, nullptr);
    const char* lang = env->GetStringUTFChars(langJ, nullptr);

    std::vector<float> samples = read_wav(wavPath);
    if (samples.empty()) {
        env->ReleaseStringUTFChars(wavJ, wavPath);
        env->ReleaseStringUTFChars(langJ, lang);
        return env->NewStringUTF("");
    }

    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.language        = lang;
    params.n_threads       = 4;
    params.translate       = false;
    params.no_context      = true;
    params.single_segment  = false;
    params.print_progress  = false;
    params.print_realtime  = false;
    params.print_timestamps = false;
    params.tdrz_enable     = false;

    params.progress_callback          = progress_cb;
    params.new_segment_callback        = segment_cb;

    LOGD("whisper_full START: %zu samples, lang=%s, threads=%d", samples.size(), lang, params.n_threads);

    int ret = whisper_full(ctx, params, samples.data(), static_cast<int>(samples.size()));
    LOGD("whisper_full DONE: ret=%d", ret);

    env->ReleaseStringUTFChars(wavJ, wavPath);
    env->ReleaseStringUTFChars(langJ, lang);

    if (ret != 0) return env->NewStringUTF("");

    int n = whisper_full_n_segments(ctx);
    LOGD("Total segments: %d", n);

    std::string result;
    for (int i = 0; i < n; i++) {
        const char* text = whisper_full_get_segment_text(ctx, i);
        if (text && strlen(text) > 0) {
            int64_t t0 = whisper_full_get_segment_t0(ctx, i);
            int64_t t1 = whisper_full_get_segment_t1(ctx, i);
            char ts[48];
            snprintf(ts, sizeof(ts), "[%02d:%02d-%02d:%02d] ", (int)(t0/100/60), (int)(t0/100%60), (int)(t1/100/60), (int)(t1/100%60));
            if (!result.empty()) result += "\n";
            result += ts;
            result += text;
        }
    }
    LOGD("Result: %zu chars => \"%s\"", result.size(), result.c_str());
    return env->NewStringUTF(result.c_str());
}

JNIEXPORT void JNICALL
Java_com_grepiu_aidiary_data_slm_WhisperEngine_nativeFree(JNIEnv*, jclass, jlong ptr) {
    auto* ctx = reinterpret_cast<struct whisper_context*>(ptr);
    if (ctx) { whisper_free(ctx); LOGD("Freed"); }
}

}
