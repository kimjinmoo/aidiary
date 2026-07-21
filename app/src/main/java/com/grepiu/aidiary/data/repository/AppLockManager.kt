package com.grepiu.aidiary.data.repository

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest

/**
 * 다이어리 앱 자물쇠(비밀번호 잠금) 기능을 관리하는 저장소 매니저입니다.
 * 4자리 비밀번호는 SHA-256으로 안전하게 해싱하여 SharedPreferences에 영속화합니다.
 */
class AppLockManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /**
     * 자물쇠(앱 잠금) 옵션 활성화 여부
     */
    var isLockEnabled: Boolean
        get() = prefs.getBoolean(KEY_LOCK_ENABLED, false)
        private set(value) {
            prefs.edit().putBoolean(KEY_LOCK_ENABLED, value).apply()
        }

    /**
     * 저장된 비밀번호 해시값
     */
    private val savedPinHash: String?
        get() = prefs.getString(KEY_PIN_HASH, null)

    /**
     * 신규 4자리 비밀번호 저장 및 잠금 활성화
     */
    fun savePin(pin: String) {
        val hash = hashPin(pin)
        // 디스크 디바이스 물리 영속화를 위해 commit() 실행
        prefs.edit()
            .putString(KEY_PIN_HASH, hash)
            .putBoolean(KEY_LOCK_ENABLED, true)
            .commit()
    }

    /**
     * 입력된 비밀번호 검증
     */
    fun verifyPin(pin: String): Boolean {
        val hash = hashPin(pin)
        return savedPinHash != null && savedPinHash == hash
    }

    /**
     * 자물쇠 잠금 해제 및 비밀번호 정보 초기화
     */
    fun clearLock() {
        prefs.edit()
            .putBoolean(KEY_LOCK_ENABLED, false)
            .remove(KEY_PIN_HASH)
            .commit()
    }

    /**
     * SHA-256 해시 함수
     */
    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(pin.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val PREF_NAME = "ai_diary_app_lock_prefs"
        private const val KEY_LOCK_ENABLED = "key_lock_enabled"
        private const val KEY_PIN_HASH = "key_pin_hash"
    }
}
