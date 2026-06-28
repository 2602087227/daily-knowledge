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
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
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
    private lateinit var ttsManager: TtsManager
    private var currentItem: KnowledgeItem? = null
    // 必须用 Main 线程 — startForeground / NotificationManager.notify 须在主线程调用
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isTtsReady = false

    companion object {
        const val CHANNEL_ID = "daily_knowledge_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP_SERVICE = "com.dailyknowledge.action.STOP_SERVICE"

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
         * 发送更新通知的广播（用于 WorkManager 或外部触发刷新）
         */
        fun sendRefreshBroadcast(context: Context) {
            val intent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_REFRESH
            }
            context.sendBroadcast(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = KnowledgeRepository(this)
        ttsManager = TtsManager(this)

        // 监听 TTS 就绪状态
        ttsManager.setOnTtsReady { ready ->
            isTtsReady = ready
            refreshNotification()
        }

        // 监听 TTS 朗读状态
        ttsManager.setOnStatusChanged { speaking ->
            refreshNotification()
        }

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        // 加载知识并显示通知
        serviceScope.launch {
            val item = repository.getCurrentPushItem()
            if (item != null) {
                currentItem = item
                showNotification(item)
            }
        }

        // START_STICKY：服务被杀后自动重启，但需要重新加载内容
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * 显示/更新通知
     */
    private suspend fun showNotification(item: KnowledgeItem) {
        currentItem = item
        val remoteViews = buildRemoteViews(item)
        val isFavorite = repository.getItemById(item.id)?.isFavorite ?: false
        updateFavoriteButton(remoteViews, isFavorite)
        updateTtsButton(remoteViews)

        val notification = buildNotification(remoteViews)
        startForeground(NOTIFICATION_ID, notification)
    }

    /**
     * 刷新通知（不改变内容，仅更新按钮状态）
     */
    private fun refreshNotification() {
        val item = currentItem ?: return
        serviceScope.launch {
            val remoteViews = buildRemoteViews(item)
            val isFavorite = repository.getItemById(item.id)?.isFavorite ?: false
            updateFavoriteButton(remoteViews, isFavorite)
            updateTtsButton(remoteViews)

            val notification = buildNotification(remoteViews)
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, notification)
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
            ttsManager.isSpeaking() -> "⏹ 停止"
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
