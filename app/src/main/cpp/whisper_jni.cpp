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

static std::vector<float> read_wav(const char* path) {
    std::ifstream file(path, std::ios::binary);
    if (!file) {
        LOGE("read_wav: cannot open file: %s", path);
        return {};
    }

    char header[44];
    file.read(header, 44);
    if (std::strncmp(header, "RIFF", 4) != 0 || std::strncmp(header + 8, "WAVE", 4) != 0) {
        LOGE("read_wav: invalid WAV header");
        return {};
    }

    int bitsPerSample = *(short*)(header + 34);
    int dataSize = *(int*)(header + 40);

    LOGD("read_wav: bits=%d, dataSize=%d bytes", bitsPerSample, dataSize);

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
    LOGD("read_wav: %zu float samples loaded", samples.size());
    return samples;
}

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_grepiu_aidiary_data_slm_WhisperEngine_nativeInit(
    JNIEnv* env, jclass, jstring modelPathJ) {

    const char* modelPath = env->GetStringUTFChars(modelPathJ, nullptr);
    LOGD("nativeInit: loading model from %s", modelPath);

    struct whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;
    cparams.flash_attn = false;
    struct whisper_context* ctx = whisper_init_from_file_with_params(modelPath, cparams);

    env->ReleaseStringUTFChars(modelPathJ, modelPath);

    if (!ctx) {
        LOGE("nativeInit: failed");
        return 0;
    }
    LOGD("nativeInit: success, ptr=%p", ctx);
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jstring JNICALL
Java_com_grepiu_aidiary_data_slm_WhisperEngine_nativeTranscribe(
    JNIEnv* env, jclass, jlong ptr, jstring wavPathJ, jstring languageJ) {

    auto* ctx = reinterpret_cast<struct whisper_context*>(ptr);
    if (!ctx) return env->NewStringUTF("");

    const char* wavPath = env->GetStringUTFChars(wavPathJ, nullptr);
    const char* lang = env->GetStringUTFChars(languageJ, nullptr);

    LOGD("nativeTranscribe: reading WAV from %s", wavPath);
    std::vector<float> samples = read_wav(wavPath);
    if (samples.empty()) {
        LOGE("nativeTranscribe: empty samples, returning empty");
        env->ReleaseStringUTFChars(wavPathJ, wavPath);
        env->ReleaseStringUTFChars(languageJ, lang);
        return env->NewStringUTF("");
    }

    float durationSec = (float)samples.size() / 16000.0f;
    LOGD("nativeTranscribe: samples=%zu, duration=%.1fs, starting whisper_full", samples.size(), durationSec);

    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.language         = lang;
    params.print_progress   = false;
    params.print_realtime   = false;
    params.print_timestamps = false;
    params.no_context       = true;
    params.tdrz_enable      = false;
    if (params.n_threads <= 0) {
        params.n_threads = 4;
    }

    int ret = whisper_full(ctx, params, samples.data(), static_cast<int>(samples.size()));
    LOGD("nativeTranscribe: whisper_full returned %d", ret);

    if (ret != 0) {
        LOGE("nativeTranscribe: whisper_full failed with code %d", ret);
        env->ReleaseStringUTFChars(wavPathJ, wavPath);
        env->ReleaseStringUTFChars(languageJ, lang);
        return env->NewStringUTF("");
    }

    const int n_segments = whisper_full_n_segments(ctx);
    LOGD("nativeTranscribe: %d segments", n_segments);

    std::string result;
    int speakerIdx = 0;
    const char* speakerLabels[] = {"화자A", "화자B", "화자C", "화자D"};

    for (int i = 0; i < n_segments; i++) {
        const char* text = whisper_full_get_segment_text(ctx, i);
        if (text && strlen(text) > 0) {
            if (params.tdrz_enable) {
                // 화자 전환 감지 (TDRZ 활성화 시에만 동작)
                if (i > 0 && whisper_full_get_segment_speaker_turn_next(ctx, i - 1)) {
                    speakerIdx = (speakerIdx + 1) % 4;
                }
                int64_t t0 = whisper_full_get_segment_t0(ctx, i);
                int64_t t1 = whisper_full_get_segment_t1(ctx, i);
                int sec0 = (int)(t0 / 100);
                int min0 = sec0 / 60; sec0 %= 60;
                int sec1 = (int)(t1 / 100);
                int min1 = sec1 / 60; sec1 %= 60;
                
                char ts[64];
                snprintf(ts, sizeof(ts), "[%02d:%02d-%02d:%02d] %s: ", min0, sec0, min1, sec1, speakerLabels[speakerIdx]);
                
                if (!result.empty()) result += "\n";
                result += ts;
                result += text;
            } else {
                // TDRZ 비활성화 시에는 타임스탬프와 화자 구분 없이 깨끗한 텍스트만 합쳐서 반환
                if (!result.empty()) result += " ";
                result += text;
            }
        }
    }

    env->ReleaseStringUTFChars(wavPathJ, wavPath);
    env->ReleaseStringUTFChars(languageJ, lang);

    LOGD("nativeTranscribe: result length=%zu, result=%s", result.size(), result.c_str());
    return env->NewStringUTF(result.c_str());
}

JNIEXPORT void JNICALL
Java_com_grepiu_aidiary_data_slm_WhisperEngine_nativeFree(
    JNIEnv*, jclass, jlong ptr) {
    auto* ctx = reinterpret_cast<struct whisper_context*>(ptr);
    if (ctx) {
        whisper_free(ctx);
        LOGD("nativeFree: done");
    }
}

} // extern "C"
