package com.grepiu.aidiary.data.slm

import android.app.ActivityManager
import android.content.Context
import android.opengl.EGL14
import android.util.Log

data class DeviceCapability(
    val isSupported: Boolean,
    val totalRamGB: Float,
    val gpuRenderer: String,
    val reason: String? = null
)

/**
 * 디바이스 하드웨어 사양(RAM, GPU OpenCL)이 온디바이스 LLM을 돌리기에 적합한지 판단해주는 클래스입니다.
 */
object DeviceCapabilityChecker {

    private const val TAG = "DeviceCapabilityChecker"
    private const val MIN_RAM_GB = 4f // 최소 실행 RAM을 4GB로 하향 (6GB 미만은 경고만 제공)

    fun check(context: Context): DeviceCapability {
        val ramGB = getTotalRamGB(context)

        // 1. RAM 사양 체크 (최소 4GB)
        if (ramGB < MIN_RAM_GB) {
            return DeviceCapability(
                isSupported = false,
                totalRamGB = ramGB,
                gpuRenderer = "",
                reason = "죄송합니다. 이 기기는 RAM이 ${ramGB.toInt()}GB로,\n" +
                        "온디바이스 AI 구동에 필요한 최소 사양(${MIN_RAM_GB.toInt()}GB)을 충족하지 못합니다.\n\n" +
                        "보다 원활한 이용을 위해 RAM 6GB 이상의 기기에서 실행해 주세요."
            )
        }

        val gpuRenderer = getGpuRenderer()
        val isRealGpu = isRealGpu(gpuRenderer)
        val hasOpenCL = isRealGpu && hasOpenCL()

        // 2. GPU 가속 OpenCL 체크 (경고 수준으로 조정하고 다운로드 자체는 차단하지 않음)
        if (!hasOpenCL && isRealGpu) {
            Log.w(TAG, "OpenCL is not detected, but letting user proceed with download. GPU: $gpuRenderer")
        }

        return DeviceCapability(
            isSupported = true, // 다운로드 및 실행 유도
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
            // 무시하고 직접 경로 체크 시도
        }

        // 방법 2: 시스템 내부 라이브러리 존재 여부 직접 검사 (링커 네임스페이스 제약 우회)
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
            if (java.io.File(path).exists()) {
                return true
            }
        }
        return false
    }
}
