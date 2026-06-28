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
 * 前台服务 — 在通知栏显示常驻每日知识通知
 * 使用自定义 RemoteViews 布局，支持按钮交互
 */
class DailyNotificationService : Service() {

    private lateinit var repository: KnowledgeRepository
    private var currentItem: KnowledgeItem? = null
    // 必须用 Main 线程 — startForeground / NotificationManager.notify 须在主线程调用
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isTtsReady = false

    /** 取得 Application 级 TTS 单例（可能为 null） */
    private val ttsManager: TtsManager?
        get() = DailyKnowledgeApp.getInstance().ttsManager

    companion object {
        private const val TAG = "DailyNotificationSvc"
        const val CHANNEL_ID = "daily_knowledge_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP_SERVICE = "com.dailyknowledge.action.STOP_SERVICE"
        const val ACTION_REFRESH_CONTENT = "com.dailyknowledge.action.REFRESH_CONTENT"

        /** 通知更新锁 — Service 与 BroadcastReceiver 共享，防止并发更新导致 RemoteViews 异常 */
        val notificationUpdateLock = Any()

        /** 当前通知中展示的知识条目 — Service 与 Receiver 共享，防止 TTS 回调时回退内容 */
        @Volatile
        var currentNotificationItem: KnowledgeItem? = null

        /**
         * 启动前台服务并显示通知
         */
        fun start(context: Context) {
            val intent = Intent(context, DailyNotificationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * 停止前台服务
         */
        fun stop(context: Context) {
            val intent = Intent(context, DailyNotificationService::class.java)
            context.stopService(intent)
        }

        /**
         * 触发每日刷新（用于 WorkManager 或外部触发）
         * 直接启动前台服务并传入 REFRESH_CONTENT action
         */
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

        // 监听 TTS 就绪状态（使用 App 单例）
        ttsManager?.setOnTtsReady { ready ->
            isTtsReady = ready
            refreshNotification()
        }

        // 监听 TTS 朗读状态
        ttsManager?.setOnStatusChanged { speaking ->
            refreshNotification()
        }

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when {
            intent?.action == ACTION_STOP_SERVICE -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            intent?.action == ACTION_REFRESH_CONTENT -> {
                // 每日刷新：确保前台服务运行 + 推进每日推送
                try {
                    startForeground(NOTIFICATION_ID, buildPlaceholderNotification())
                    serviceScope.launch {
                        try {
                            val item = repository.getDailyPushItem()
                            if (item != null) {
                                currentItem = item
                                showNotification(item)
                            } else {
                                updatePlaceholderText("请先导入知识文件")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "加载通知内容失败", e)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "启动前台服务失败", e)
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
            else -> {
                // 普通启动：显示当前知识
                try {
                    startForeground(NOTIFICATION_ID, buildPlaceholderNotification())
                    serviceScope.launch {
                        try {
                            val item = repository.getCurrentPushItem()
                            if (item != null) {
                                currentItem = item
                                showNotification(item)
                            } else {
                                updatePlaceholderText("请先导入知识文件")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "加载通知内容失败", e)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "启动前台服务失败", e)
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
        }

        return START_STICKY
    }

    /** 构建占位通知（满足 startForeground 必须立即调用的要求） */
    private fun buildPlaceholderNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val contentIntent = PendingIntent.getActivity(this, 0, openIntent, flags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText("加载中…")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /** 更新占位通知的文字提示 */
    private fun updatePlaceholderText(text: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    else PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * 显示/更新通知
     */
    private suspend fun showNotification(item: KnowledgeItem) {
        currentItem = item
        currentNotificationItem = item  // 同步共享状态
        val remoteViews = buildRemoteViews(item)
        val isFavorite = repository.getItemById(item.id)?.isFavorite ?: false
        updateFavoriteButton(remoteViews, isFavorite)
        updateTtsButton(remoteViews)

        val notification = buildNotification(remoteViews)
        startForeground(NOTIFICATION_ID, notification)
    }

    /**
     * 刷新通知（不改变内容，仅更新按钮状态）
     * 使用共享的 currentNotificationItem，防止 Receiver 更新内容后被回退
     */
    private fun refreshNotification() {
        val item = currentNotificationItem ?: return
        serviceScope.launch {
            synchronized(notificationUpdateLock) {
                val remoteViews = buildRemoteViews(item)
                val isFavorite = repository.getItemById(item.id)?.isFavorite ?: false
                updateFavoriteButton(remoteViews, isFavorite)
                updateTtsButton(remoteViews)

                val notification = buildNotification(remoteViews)
                val manager = getSystemService(NotificationManager::class.java)
                manager.notify(NOTIFICATION_ID, notification)
            }
        }
    }

    /**
     * 更新通知内容为新条目
     */
    suspend fun updateContent(newItem: KnowledgeItem) {
        currentItem = newItem
        showNotification(newItem)
    }

    /**
     * 构建 RemoteViews
     */
    private suspend fun buildRemoteViews(item: KnowledgeItem): RemoteViews {
        val remoteViews = RemoteViews(packageName, R.layout.notification_daily_knowledge)

        // 设置知识内容
        remoteViews.setTextViewText(R.id.tv_knowledge_content, item.content)

        // 设置进度：第n条/共m条
        val totalCount = repository.getActiveFile()?.knowledgeCount ?: 0
        val progressText = "${item.indexInFile + 1}/$totalCount"
        remoteViews.setTextViewText(R.id.tv_progress, progressText)

        // 绑定按钮点击事件
        setButtonClick(remoteViews, R.id.btn_prev, NotificationActionReceiver.ACTION_NAV_PREV)
        setButtonClick(remoteViews, R.id.btn_next, NotificationActionReceiver.ACTION_NAV_NEXT)
        setButtonClick(remoteViews, R.id.btn_read_aloud, NotificationActionReceiver.ACTION_TTS_TOGGLE)
        setButtonClick(remoteViews, R.id.btn_favorite, NotificationActionReceiver.ACTION_FAVORITE_TOGGLE)
        setButtonClick(remoteViews, R.id.btn_share, NotificationActionReceiver.ACTION_SHARE)

        return remoteViews
    }

    /**
     * 绑定按钮 PendingIntent（发送广播到 NotificationActionReceiver）
     */
    private fun setButtonClick(remoteViews: RemoteViews, viewId: Int, action: String) {
        val intent = Intent(this, NotificationActionReceiver::class.java).apply {
            this.action = action
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(this, action.hashCode(), intent, flags)
        remoteViews.setOnClickPendingIntent(viewId, pendingIntent)
    }

    /**
     * 更新收藏按钮文字
     */
    private fun updateFavoriteButton(remoteViews: RemoteViews, isFavorite: Boolean) {
        val text = if (isFavorite) "★ 已收藏" else "☆ 收藏"
        remoteViews.setTextViewText(R.id.btn_favorite, text)
    }

    /**
     * 更新朗读按钮文字
     */
    private fun updateTtsButton(remoteViews: RemoteViews) {
        val text = when {
            !isTtsReady -> "🔇 朗读"
            ttsManager?.isSpeaking() == true -> "⏹ 停止"
            else -> "🔊 朗读"
        }
        remoteViews.setTextViewText(R.id.btn_read_aloud, text)
    }

    /**
     * 构建 Notification 对象
     */
    private fun buildNotification(remoteViews: RemoteViews): Notification {
        // 点击通知打开主界面
        val openIntent = Intent(this, MainActivity::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val contentIntent = PendingIntent.getActivity(this, 0, openIntent, flags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(remoteViews)
            .setCustomBigContentView(remoteViews)
            .setContentIntent(contentIntent)
            .setOngoing(true)           // 不可滑动删除
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
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

    override fun onDestroy() {
        serviceScope.cancel()
        // 不在这里释放 TTS，因为 NotificationActionReceiver 可能还需要用
        super.onDestroy()
    }
}
