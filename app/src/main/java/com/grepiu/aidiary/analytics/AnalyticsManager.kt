package com.grepiu.aidiary.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

object AnalyticsManager {

    private var instance: FirebaseAnalytics? = null

    fun init(context: Context) {
        instance = FirebaseAnalytics.getInstance(context.applicationContext)
    }

    fun logEvent(eventName: String, params: Map<String, String>? = null) {
        val bundle = params?.let {
            Bundle().apply { it.forEach { (k, v) -> putString(k, v) } }
        }
        instance?.logEvent(eventName, bundle)
    }

    fun logScreenView(screenName: String, screenClass: String? = null) {
        val params = mutableMapOf(
            FirebaseAnalytics.Param.SCREEN_NAME to screenName
        )
        screenClass?.let { params[FirebaseAnalytics.Param.SCREEN_CLASS] = it }
        instance?.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, Bundle().apply {
            putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
            screenClass?.let { putString(FirebaseAnalytics.Param.SCREEN_CLASS, it) }
        })
    }

    fun logButtonClick(buttonName: String, location: String? = null) {
        val params = mutableMapOf("button_name" to buttonName)
        location?.let { params["location"] = it }
        logEvent("button_click", params)
    }
}

object AnalyticsEvents {
    // ── Screen views ──
    const val SCREEN_DIARY_LIST = "screen_diary_list"
    const val SCREEN_DIARY_WRITE = "screen_diary_write"
    const val SCREEN_DIARY_DETAIL = "screen_diary_detail"
    const val SCREEN_DIARY_SPLASH = "screen_diary_splash"
    const val SCREEN_DIARY_SETTINGS = "screen_diary_settings"

    // ── Content Actions ──
    const val WRITE_DIARY_CREATE = "write_diary_create"
    const val WRITE_DIARY_EDIT = "write_diary_edit"
    const val WRITE_DIARY_DELETE = "write_diary_delete"
    const val WRITE_BLOCK_ADD = "write_block_add"
    const val WRITE_BLOCK_REMOVE = "write_block_remove"
    const val WRITE_VOICE_RECORD_START = "write_voice_record_start"
    const val WRITE_VOICE_RECORD_STOP = "write_voice_record_stop"
    const val WRITE_VOICE_LANGUAGE_CHANGE = "write_voice_language_change"
    const val WRITE_MEDIA_PHOTO_ADD = "write_media_photo_add"
    const val WRITE_MEDIA_VIDEO_ADD = "write_media_video_add"
    const val WRITE_MEDIA_CLOUD_ADD = "write_media_cloud_add"

    // ── Navigation ──
    const val NAV_VIEW_MODE = "nav_view_mode"
    const val NAV_TAB = "nav_tab"
    const val NAV_SEARCH = "nav_search"
    const val NAV_SEARCH_CLEAR = "nav_search_clear"
    const val NAV_DATE_SELECT = "nav_date_select"
    const val NAV_OPEN_SETTINGS = "nav_open_settings"
    const val NAV_OPEN_AI_SHEET = "nav_open_ai_sheet"
    const val NAV_PAGE_LOAD_MORE = "nav_page_load_more"

    // ── AI ──
    const val AI_CHAT_SEND = "ai_chat_send"
    const val AI_CHAT_CLEAR = "ai_chat_clear"
    const val AI_PRESET = "ai_preset"
    const val AI_MODEL_DOWNLOAD_START = "ai_model_download_start"
    const val AI_MODEL_DOWNLOAD_CANCEL = "ai_model_download_cancel"
    const val AI_BRIEFING_REQUEST = "ai_briefing_request"
    const val AI_SUGGEST_TITLE = "ai_suggest_title"
    const val AI_CLASSIFY_TYPE = "ai_classify_type"
    const val AI_PROOFREAD = "ai_proofread"
    const val AI_DECORATE = "ai_decorate"
    const val AI_TRANSLATE = "ai_translate"
    const val AI_SUGGEST_HASHTAG = "ai_suggest_hashtag"
    const val AI_SUGGEST_PLANNER_TASK = "ai_suggest_planner_task"

    // ── Social (Blog) ──
    const val BLOG_COPY = "blog_copy"
    const val BLOG_SHARE = "blog_share"
    const val BLOG_ITEM_CLICK = "blog_item_click"

    // ── Planner / Goals ──
    const val PLANNER_TASK_ADD = "planner_task_add"
    const val PLANNER_TASK_TOGGLE = "planner_task_toggle"
    const val PLANNER_TASK_DELETE = "planner_task_delete"
    const val PLANNER_TASK_SERIES_DELETE = "planner_task_series_delete"
    const val GOALS_ADD = "goals_add"
    const val GOALS_TOGGLE = "goals_toggle"
    const val GOALS_DELETE = "goals_delete"

    // ── Backup ──
    const val BACKUP_EXPORT_REQUEST = "backup_export_request"
    const val BACKUP_EXPORT_SUCCESS = "backup_export_success"
    const val BACKUP_IMPORT_REQUEST = "backup_import_request"
    const val BACKUP_IMPORT_SUCCESS = "backup_import_success"

    // ── Settings ──
    const val SETTINGS_THEME_CHANGE = "settings_theme_change"
    const val SETTINGS_LICENSE_OPEN = "settings_license_open"

    // ── Params ──
    const val PARAM_VIEW_MODE = "view_mode"
    const val PARAM_TAB = "tab_name"
    const val PARAM_CONTENT_TYPE = "content_type"
    const val PARAM_BLOCK_TYPE = "block_type"
    const val PARAM_ACTION = "action"
    const val PARAM_LOCATION = "location"
    const val PARAM_PRESET_KIND = "preset_kind"
    const val PARAM_EMOTION = "emotion"
    const val PARAM_LANGUAGE = "language"
}
