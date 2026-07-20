package com.grepiu.aidiary.data.remote

import android.util.Log
import com.grepiu.aidiary.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class AppVersionResult(
    val latestVersion: String?,
    val updateAvailable: Boolean,
    val error: String? = null
)

object AppVersionChecker {

    private const val TAG = "AppVersionChecker"
    private const val BASE_URL = "https://conf.grepiu.com"
    private const val APP_PACKAGE = "com.grepiu.aidiary"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun check(): AppVersionResult = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/common-api/v1/app/version?os=ANDROID&appPackage=$APP_PACKAGE"
            val request = Request.Builder().url(url).get().build()
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                Log.w(TAG, "Version check HTTP ${response.code}: $body")
                return@withContext AppVersionResult(null, false, "HTTP ${response.code}")
            }

            val json = JSONObject(body)
            val code = json.optString("code", "")
            val latestVersion = json.optString("message", "")

            if (code != "200" || latestVersion.isBlank()) {
                Log.w(TAG, "Version check not ok: code=$code message=$latestVersion")
                return@withContext AppVersionResult(null, false, "code=$code")
            }

            val currentVersion = BuildConfig.VERSION_NAME
            val updateAvailable = isUpdateAvailable(currentVersion, latestVersion)
            Log.d(TAG, "Current=$currentVersion Latest=$latestVersion Update=$updateAvailable")

            AppVersionResult(latestVersion, updateAvailable)
        } catch (e: Exception) {
            Log.w(TAG, "Version check failed: ${e.message}")
            AppVersionResult(null, false, e.message)
        }
    }

    fun isUpdateAvailable(currentVersion: String, latestVersion: String): Boolean {
        val current = currentVersion.split(".").map { it.toIntOrNull() ?: 0 }
        val latest = latestVersion.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(current.size, latest.size)) {
            val c = current.getOrElse(i) { 0 }
            val l = latest.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }
}
