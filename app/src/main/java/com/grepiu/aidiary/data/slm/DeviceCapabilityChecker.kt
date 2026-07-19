package com.grepiu.aidiary.data.slm

import android.app.ActivityManager
import android.content.Context
import android.opengl.EGL14
import android.util.Log

enum class ModelPurpose { LLM, SHERPA }

data class DeviceCapability(
    val isSupported: Boolean,
    val totalRamGB: Float,
    val gpuRenderer: String,
    val reason: String? = null
)

/**
 * 디바이스 하드웨어 사양(RAM, GPU OpenCL)이 온디바이스 AI 모델(LLM/Sherpa)을 돌리기에 적합한지 판단해주는 클래스입니다.
 */
object DeviceCapabilityChecker {

    private const val TAG = "DeviceCapabilityChecker"
    private const val MIN_RAM_GB_LLM = 6f

    fun isLowRamDevice(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return memoryInfo.totalMem <= 6L * 1024 * 1024 * 1024
    }

    fun check(context: Context, purpose: ModelPurpose = ModelPurpose.LLM): DeviceCapability {
        val ramGB = getTotalRamGB(context)
        val lowRam = isLowRamDevice(context)

        if (purpose == ModelPurpose.LLM) {
            if (lowRam || ramGB < MIN_RAM_GB_LLM) {
                return DeviceCapability(
                    isSupported = false,
                    totalRamGB = ramGB,
                    gpuRenderer = "",
                    reason = "현재 사용 중이신 스마트폰의 하드웨어 사양으로는 온디바이스 AI 언어 모델을 이용할 수 없어요."
                )
            }
        } else if (purpose == ModelPurpose.SHERPA) {
            if (ramGB < 1.0f) {
                return DeviceCapability(
                    isSupported = false,
                    totalRamGB = ramGB,
                    gpuRenderer = "",
                    reason = "기기 메모리가 부족하여 음성인식 모델을 구동할 수 없습니다."
                )
            }
        }

        val gpuRenderer = getGpuRenderer()
        val isRealGpu = isRealGpu(gpuRenderer)
        val hasOpenCL = hasOpenCL()

        // GPU 확인만 하고 OpenCL 미검출 시에도 일반 기기 다운로드 허용 (LiteRT-LM이 CPU 폴백 가능)
        if (!isRealGpu) {
            Log.w(TAG, "Not a real GPU: $gpuRenderer. Allowing download anyway.")
        }
        if (!hasOpenCL) {
            Log.w(TAG, "OpenCL not detected but download allowed. GPU: $gpuRenderer")
        }

        return DeviceCapability(
            isSupported = true,
            totalRamGB = ramGB,
            gpuRenderer = gpuRenderer
        )
    }

    private fun getTotalRamGB(context: Context): Float {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return 0f
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return memoryInfo.totalMem / (1024f * 1024f * 1024f)
    }

    private fun getGpuRenderer(): String {
        return try {
            val eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (eglDisplay == EGL14.EGL_NO_DISPLAY) return "알 수 없음"

            val version = IntArray(2)
            if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) return "알 수 없음"

            val renderer = EGL14.eglQueryString(eglDisplay, EGL14.EGL_VENDOR)
                ?: "알 수 없음"

            EGL14.eglTerminate(eglDisplay)
            renderer
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query GPU renderer", e)
            "알 수 없음"
        }
    }

    private fun isRealGpu(renderer: String): Boolean {
        if (renderer.isEmpty() || renderer == "알 수 없음") return false
        val lowered = renderer.lowercase()
        val realGpuKeywords = listOf(
            "adreno", "mali", "powervr", "immortalis",
            "geforce", "tegra", "vivante", "videocore"
        )
        if (realGpuKeywords.any { lowered.contains(it) }) return true

        val emulatorKeywords = listOf(
            "swiftshader", "android emulator",
            "llvmpipe", "softpipe", "virgl",
            "android software renderer"
        )
        return emulatorKeywords.none { lowered.contains(it) }
    }

    private fun hasOpenCL(): Boolean {
        // 방법 1: System.loadLibrary
        try {
            System.loadLibrary("OpenCL")
            return true
        } catch (e: UnsatisfiedLinkError) {
            // 링커 네임스페이스 제약으로 실패할 수 있음 → 파일 경로 체크로 폴백
        }

        // 방법 2: 시스템 내부 라이브러리 존재 여부 직접 검사 (네임스페이스 제약 우회)
        val openClPaths = listOf(
            "/system/lib/libOpenCL.so",
            "/system/lib64/libOpenCL.so",
            "/vendor/lib/libOpenCL.so",
            "/vendor/lib64/libOpenCL.so",
            "/vendor/lib/egl/libGLES_mali.so",
            "/vendor/lib64/egl/libGLES_mali.so",
            "/system/vendor/lib/libOpenCL.so",
            "/system/vendor/lib64/libOpenCL.so"
        )
        for (path in openClPaths) {
            if (java.io.File(path).exists()) return true
        }
        return false
    }
}
