package com.dailyknowledge

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.work.Configuration
import androidx.work.WorkManager
import com.dailyknowledge.worker.DailyNotificationWorker
import com.dailyknowledge.util.TtsManager

/**
 * Application 类 — 应用初始化入口
 * - 创建通知渠道
 * - 初始化 WorkManager（手动控制）
 * - 调度每日推送任务
 */
class DailyKnowledgeApp : Application(), Configuration.Provider {

    /** TTS 管理器 — 应用级单例，供通知栏和 App 内共用 */
    lateinit var ttsManager: TtsManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 创建通知渠道（Android 8.0+）
        createNotificationChannel()

        // 初始化 TTS 管理器
        ttsManager = TtsManager(this)

        // 调度每日推送任务
        DailyNotificationWorker.schedule(this)
    }

    /**
     * 创建通知渠道
     */
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

    /**
     * WorkManager 配置 — 手动初始化
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onTerminate() {
        ttsManager.shutdown()
        super.onTerminate()
    }

    companion object {
        const val CHANNEL_ID = "daily_knowledge_channel"

        @Volatile
        private var instance: DailyKnowledgeApp? = null

        fun getInstance(): DailyKnowledgeApp {
            return instance ?: throw IllegalStateException("DailyKnowledgeApp 未初始化")
        }
    }
}
