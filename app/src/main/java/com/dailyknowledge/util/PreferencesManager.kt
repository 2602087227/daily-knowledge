package com.dailyknowledge.util

import android.content.Context
import android.content.SharedPreferences

/**
 * SharedPreferences 管理器
 * 存储推送索引、推送日期、激活文件 ID 等轻量状态
 */
class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "daily_knowledge_prefs"
        private const val KEY_CURRENT_PUSH_INDEX = "current_push_index"
        private const val KEY_LAST_PUSH_DATE = "last_push_date"
        private const val KEY_ACTIVE_FILE_ID = "active_file_id"
        private const val KEY_NOTIFICATION_HOUR = "notification_hour"
        private const val KEY_NOTIFICATION_MINUTE = "notification_minute"

        /** 默认推送时间：早上 8:00 */
        const val DEFAULT_HOUR = 8
        const val DEFAULT_MINUTE = 0
    }

    // ==================== 推送索引 ====================

    /** 获取当前推送索引 */
    fun getCurrentPushIndex(): Int {
        return prefs.getInt(KEY_CURRENT_PUSH_INDEX, 0)
    }

    /** 设置推送索引 */
    fun setCurrentPushIndex(index: Int) {
        prefs.edit().putInt(KEY_CURRENT_PUSH_INDEX, index).apply()
    }

    /** 重置推送索引为 0 */
    fun resetPushIndex() {
        prefs.edit().putInt(KEY_CURRENT_PUSH_INDEX, 0).apply()
    }

    // ==================== 推送日期 ====================

    /** 获取上次推送日期（格式 yyyyMMdd），空字符串表示从未推送 */
    fun getLastPushDate(): String {
        return prefs.getString(KEY_LAST_PUSH_DATE, "") ?: ""
    }

    /** 设置上次推送日期 */
    fun setLastPushDate(date: String) {
        prefs.edit().putString(KEY_LAST_PUSH_DATE, date).apply()
    }

    // ==================== 激活文件 ID ====================

    /** 获取当前激活文件 ID */
    fun getActiveFileId(): Long {
        return prefs.getLong(KEY_ACTIVE_FILE_ID, -1L)
    }

    /** 设置激活文件 ID */
    fun setActiveFileId(fileId: Long) {
        prefs.edit().putLong(KEY_ACTIVE_FILE_ID, fileId).apply()
    }

    /** 清除激活文件 ID */
    fun clearActiveFileId() {
        prefs.edit().remove(KEY_ACTIVE_FILE_ID).apply()
    }

    // ==================== 通知时间 ====================

    /** 获取通知小时（0-23） */
    fun getNotificationHour(): Int {
        return prefs.getInt(KEY_NOTIFICATION_HOUR, DEFAULT_HOUR)
    }

    /** 获取通知分钟（0-59） */
    fun getNotificationMinute(): Int {
        return prefs.getInt(KEY_NOTIFICATION_MINUTE, DEFAULT_MINUTE)
    }

    /** 设置通知时间 */
    fun setNotificationTime(hour: Int, minute: Int) {
        prefs.edit()
            .putInt(KEY_NOTIFICATION_HOUR, hour)
            .putInt(KEY_NOTIFICATION_MINUTE, minute)
            .apply()
    }
}
