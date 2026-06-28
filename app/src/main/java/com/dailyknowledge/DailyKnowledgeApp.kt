package com.dailyknowledge

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import androidx.work.Configuration
import com.dailyknowledge.worker.DailyNotificationWorker
import com.dailyknowledge.util.TtsManager

/**
 * Application 类 — 应用初始化入口
 */
class DailyKnowledgeApp : Application(), Configuration.Provider {

    var ttsManager: TtsManager? = null
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        try {
            createNotificationChannel()
        } catch (e: Exception) {
            Log.e(TAG, "创建通知渠道失败", e)
        }

        try {
            ttsManager = TtsManager(this)
        } catch (e: Exception) {
            Log.e(TAG, "初始化 TTS 失败", e)
        }

        try {
            DailyNotificationWorker.schedule(this)
        } catch (e: Exception) {
            Log.e(TAG, "调度通知任务失败", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onTerminate() {
        try {
            ttsManager?.shutdown()
        } catch (_: Exception) {}
        super.onTerminate()
    }

    companion object {
        const val CHANNEL_ID = "daily_knowledge_channel"
        private const val TAG = "DailyKnowledgeApp"

        @Volatile
        private var instance: DailyKnowledgeApp? = null

        fun getInstance(): DailyKnowledgeApp {
            return instance ?: throw IllegalStateException("DailyKnowledgeApp 未初始化")
        }
    }
}
