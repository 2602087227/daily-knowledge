package com.dailyknowledge.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.dailyknowledge.DailyKnowledgeApp
import com.dailyknowledge.R
import com.dailyknowledge.data.repository.KnowledgeRepository
import com.dailyknowledge.data.model.KnowledgeItem
import com.dailyknowledge.ui.MainActivity
import com.dailyknowledge.util.TtsManager
import kotlinx.coroutines.*

/**
 * 前台服务 — 音乐播放器风格的常驻通知
 * - startForeground() 仅在首次调用，后续统一用 NotificationManager.notify()
 * - 固定 Notification ID，永远只有一条通知
 * - setOnlyAlertOnce(true)：更新时不提示、不震动、不播放声音
 * - 自定义 RemoteViews 布局，支持大文字、多行、按钮交互
 */
class DailyNotificationService : Service() {

    private lateinit var repository: KnowledgeRepository
    private var currentItem: KnowledgeItem? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isTtsReady = false

    /** startForeground() 是否已调用 — 确保只调用一次 */
    private var hasStartedForeground = false

    /** 取得 Application 级 TTS 单例（可能为 null） */
    private val ttsManager: TtsManager?
        get() = DailyKnowledgeApp.getInstance().ttsManager

    companion object {
        private const val TAG = "DailyNotificationSvc"
        const val CHANNEL_ID = "daily_knowledge_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP_SERVICE = "com.dailyknowledge.action.STOP_SERVICE"
        const val ACTION_REFRESH_CONTENT = "com.dailyknowledge.action.REFRESH_CONTENT"
        const val ACTION_TTS = "com.dailyknowledge.action.SVC_TTS"

        /** 通知更新锁 — Service 与 BroadcastReceiver 共享 */
        val notificationUpdateLock = Any()

        /** 当前通知中展示的知识条目 — Service 与 Receiver 共享 */
        @Volatile
        var currentNotificationItem: KnowledgeItem? = null

        fun start(context: Context) {
            val intent = Intent(context, DailyNotificationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, DailyNotificationService::class.java)
            context.stopService(intent)
        }

        fun triggerDailyRefresh(context: Context) {
            val intent = Intent(context, DailyNotificationService::class.java).apply {
                action = ACTION_REFRESH_CONTENT
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = KnowledgeRepository(this)

        // 监听 TTS 就绪状态
        ttsManager?.setOnTtsReady { ready ->
            isTtsReady = ready
            refreshNotification()
        }

        // 监听 TTS 朗读状态
        ttsManager?.setOnStatusChanged { _ ->
            refreshNotification()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when {
            intent?.action == ACTION_STOP_SERVICE -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            intent?.action == ACTION_TTS -> {
                handleTtsAction()
            }
            intent?.action == ACTION_REFRESH_CONTENT -> {
                try {
                    val item = kotlinx.coroutines.runBlocking { repository.getDailyPushItem() }
                    if (item != null) {
                        currentItem = item
                        currentNotificationItem = item
                        val totalCount = kotlinx.coroutines.runBlocking { repository.getActiveFile()?.knowledgeCount ?: 0 }
                        updateOrStartNotification(item, totalCount)
                    } else {
                        updateOrStartPlaceholder("请先导入知识文件")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "刷新通知失败", e)
                    try { updateOrStartPlaceholder("加载失败") } catch (_: Exception) {}
                }
            }
            else -> {
                try {
                    val item = kotlinx.coroutines.runBlocking { repository.getCurrentPushItem() }
                    if (item != null) {
                        currentItem = item
                        currentNotificationItem = item
                        val totalCount = kotlinx.coroutines.runBlocking { repository.getActiveFile()?.knowledgeCount ?: 0 }
                        updateOrStartNotification(item, totalCount)
                    } else {
                        updateOrStartPlaceholder("请先导入知识文件")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "启动前台服务失败", e)
                    try { updateOrStartPlaceholder("加载失败") } catch (_: Exception) {}
                }
            }
        }

        return START_STICKY
    }

    /** 首次调用 startForeground()，后续调用 notify() 更新 */
    private fun updateOrStartNotification(item: KnowledgeItem, totalCount: Int) {
        val notification = buildNotification(item, totalCount)
        if (hasStartedForeground) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, notification)
        } else {
            startForeground(NOTIFICATION_ID, notification)
            hasStartedForeground = true
        }
    }

    /** 占位通知同样遵循首次 startForeground / 后续 notify */
    private fun updateOrStartPlaceholder(text: String) {
        val notification = buildPlaceholderNotification(text)
        if (hasStartedForeground) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, notification)
        } else {
            startForeground(NOTIFICATION_ID, notification)
            hasStartedForeground = true
        }
    }

    /** 构建占位通知 */
    private fun buildPlaceholderNotification(text: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val flags = pendingIntentFlags()
        val contentIntent = PendingIntent.getActivity(this, 0, openIntent, flags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setDefaults(0)
            .setSound(null)
            .setVibrate(longArrayOf(0))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** 刷新通知（TTS/收藏状态变化时更新按钮文字） */
    private fun refreshNotification() {
        val item = currentNotificationItem ?: return
        serviceScope.launch {
            val totalCount = repository.getActiveFile()?.knowledgeCount ?: 0
            synchronized(notificationUpdateLock) {
                val notification = buildNotification(item, totalCount)
                val manager = getSystemService(NotificationManager::class.java)
                manager.notify(NOTIFICATION_ID, notification)
            }
        }
    }

    /** TTS 朗读/停止 — 点击直达 Service */
    private fun handleTtsAction() {
        val item = currentNotificationItem ?: return
        val tts = ttsManager ?: return
        tts.speakOrStop(item.content)
    }

    /** 构建 TTS 按钮的 PendingIntent（getService 直达 Service，比 getBroadcast 更快） */
    private fun buildTtsServiceIntent(): PendingIntent {
        val intent = Intent(this, DailyNotificationService::class.java).apply {
            action = ACTION_TTS
        }
        return PendingIntent.getService(this, ACTION_TTS.hashCode(), intent, pendingIntentFlags())
    }

    /** 构建标准广播 Action 按钮的 PendingIntent */
    private fun buildActionIntent(action: String): PendingIntent {
        val intent = Intent(this, NotificationActionReceiver::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(this, action.hashCode(), intent, pendingIntentFlags())
    }

    private fun pendingIntentFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

    /** 构建 RemoteViews 通知 — 音乐播放器风格：静音、不震动、固定ID、仅刷新 */
    private fun buildNotification(item: KnowledgeItem, totalCount: Int): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val contentIntent = PendingIntent.getActivity(this, 0, openIntent, pendingIntentFlags())

        val favLabel = if (item.isFavorite) "★ 已收藏" else "☆ 收藏"
        val ttsLabel = when {
            !isTtsReady -> "🔇 朗读"
            ttsManager?.isSpeaking() == true -> "⏹ 停止"
            else -> "🔊 朗读"
        }
        val progressText = "第${item.indexInFile + 1}条/共${totalCount}条"

        // 折叠和展开都用完整布局，确保按钮始终可见
        val contentView = RemoteViews(packageName, R.layout.notification_daily_knowledge_expanded)
        contentView.setTextViewText(R.id.tv_knowledge_content, item.content)
        contentView.setTextViewText(R.id.tv_progress, progressText)
        contentView.setTextViewText(R.id.btn_prev, "◀ 上一条")
        contentView.setTextViewText(R.id.btn_next, "下一条 ▶")
        contentView.setTextViewText(R.id.btn_read_aloud, ttsLabel)
        contentView.setTextViewText(R.id.btn_favorite, favLabel)
        contentView.setTextViewText(R.id.btn_share, "📤 分享")

        // 按钮点击事件 — TTS 用 getService 直达 Service
        contentView.setOnClickPendingIntent(R.id.btn_prev, buildActionIntent(NotificationActionReceiver.ACTION_NAV_PREV))
        contentView.setOnClickPendingIntent(R.id.btn_next, buildActionIntent(NotificationActionReceiver.ACTION_NAV_NEXT))
        contentView.setOnClickPendingIntent(R.id.btn_read_aloud, buildTtsServiceIntent())
        contentView.setOnClickPendingIntent(R.id.btn_favorite, buildActionIntent(NotificationActionReceiver.ACTION_FAVORITE_TOGGLE))
        contentView.setOnClickPendingIntent(R.id.btn_share, buildActionIntent(NotificationActionReceiver.ACTION_SHARE))

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setCustomContentView(contentView)
            .setCustomBigContentView(contentView)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setDefaults(0)
            .setSound(null)
            .setVibrate(longArrayOf(0))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }
}
