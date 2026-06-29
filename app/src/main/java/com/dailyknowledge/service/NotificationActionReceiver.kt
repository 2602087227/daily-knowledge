package com.dailyknowledge.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.dailyknowledge.DailyKnowledgeApp
import com.dailyknowledge.data.repository.KnowledgeRepository
import com.dailyknowledge.util.ShareUtil
import com.dailyknowledge.util.TtsManager
import kotlinx.coroutines.*

/**
 * 通知按钮广播接收器 — 处理通知栏 6 个按钮的点击事件
 *
 * 支持的 Action：
 * - ACTION_NAV_PREV：切换到上一条知识
 * - ACTION_NAV_NEXT：切换到下一条知识
 * - ACTION_TTS_TOGGLE：朗读/停止朗读
 * - ACTION_FAVORITE_TOGGLE：收藏/取消收藏
 * - ACTION_SHARE：分享当前知识
 * - ACTION_REFRESH：从外部触发刷新通知内容
 */
class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "NotificationReceiver"

        const val ACTION_NAV_PREV = "com.dailyknowledge.action.NAV_PREV"
        const val ACTION_NAV_NEXT = "com.dailyknowledge.action.NAV_NEXT"
        const val ACTION_TTS_TOGGLE = "com.dailyknowledge.action.TTS_TOGGLE"
        const val ACTION_FAVORITE_TOGGLE = "com.dailyknowledge.action.FAVORITE_TOGGLE"
        const val ACTION_SHARE = "com.dailyknowledge.action.SHARE"
        const val ACTION_REFRESH = "com.dailyknowledge.action.REFRESH"
    }

    // 必须用 Main 线程 — NotificationManager.notify() 和相关 PendingIntent 操作须在主线程
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val repository = KnowledgeRepository(context)
        // 使用 Application 级 TTS 单例（可能为 null，如初始化失败）
        val ttsManager = DailyKnowledgeApp.getInstance().ttsManager

        when (action) {
            ACTION_NAV_PREV -> handleNavPrev(context, repository)
            ACTION_NAV_NEXT -> handleNavNext(context, repository)
            ACTION_TTS_TOGGLE -> ttsManager?.let { handleTtsToggle(context, it) }
            ACTION_FAVORITE_TOGGLE -> handleFavoriteToggle(context, repository)
            ACTION_SHARE -> handleShare(context)
            ACTION_REFRESH -> handleRefresh(context)
        }
    }

    /** 切换到上一条知识 */
    private fun handleNavPrev(context: Context, repository: KnowledgeRepository) {
        scope.launch {
            try {
                val prevItem = repository.moveToPrevItem()
                if (prevItem != null) {
                    updateNotification(context, repository, prevItem)
                }
            } catch (e: Exception) {
                Log.e(TAG, "切换到上一条失败", e)
            }
        }
    }

    /** 切换到下一条知识 */
    private fun handleNavNext(context: Context, repository: KnowledgeRepository) {
        scope.launch {
            try {
                val nextItem = repository.moveToNextItem()
                if (nextItem != null) {
                    updateNotification(context, repository, nextItem)
                }
            } catch (e: Exception) {
                Log.e(TAG, "切换到下一条失败", e)
            }
        }
    }

    /** 朗读/停止切换 — 直接用内存中的条目，无需协程和数据库查询 */
    private fun handleTtsToggle(context: Context, ttsManager: TtsManager) {
        try {
            val item = DailyNotificationService.currentNotificationItem
            if (item != null) {
                val ok = ttsManager.speakOrStop(item.content)
                if (!ok) {
                    android.widget.Toast.makeText(
                        context,
                        "朗读引擎未就绪，请检查系统 TTS 设置",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
            // TTS 状态变化会通过回调自动刷新通知
        } catch (e: Exception) {
            Log.e(TAG, "TTS 操作失败", e)
        }
    }

    /** 收藏/取消收藏 */
    private fun handleFavoriteToggle(context: Context, repository: KnowledgeRepository) {
        scope.launch {
            try {
                // 优先用内存中的条目，避免额外 DB 查询
                val item = DailyNotificationService.currentNotificationItem
                    ?: repository.getCurrentPushItem()
                if (item != null) {
                    repository.toggleFavorite(item.id)
                    // 刷新通知以更新收藏按钮状态
                    refreshServiceNotification(context, repository)
                }
            } catch (e: Exception) {
                Log.e(TAG, "收藏操作失败", e)
            }
        }
    }

    /** 分享当前知识 — 直接用内存中的条目 */
    private fun handleShare(context: Context) {
        try {
            val item = DailyNotificationService.currentNotificationItem
            if (item != null) {
                ShareUtil.shareKnowledge(context, item.content)
            }
        } catch (e: Exception) {
            Log.e(TAG, "分享失败", e)
        }
    }

    /** 刷新通知内容（由 WorkManager 等外部触发） */
    private fun handleRefresh(context: Context) {
        // 以 REFRESH_CONTENT action 启动前台服务，由 Service 统一处理每日推送逻辑
        // 避免 Service 和 Receiver 并发更新通知导致内容被覆盖
        val intent = Intent(context, DailyNotificationService::class.java).apply {
            action = DailyNotificationService.ACTION_REFRESH_CONTENT
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    /**
     * 更新通知内容 — 使用 QQ音乐风格 RemoteViews 自定义布局
     */
    private suspend fun updateNotification(
        context: Context,
        repository: KnowledgeRepository,
        item: com.dailyknowledge.data.model.KnowledgeItem
    ) {
        val totalCount = repository.getActiveFile()?.knowledgeCount ?: 0
        val ttsManager = DailyKnowledgeApp.getInstance().ttsManager

        // 同步 Service 的共享状态
        DailyNotificationService.currentNotificationItem = item

        synchronized(DailyNotificationService.notificationUpdateLock) {
            val notification = buildUpdatedNotification(context, item, totalCount, ttsManager)
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.notify(DailyNotificationService.NOTIFICATION_ID, notification)
        }
    }

    /** 刷新通知（保持内容不变，仅刷新按钮状态） */
    private suspend fun refreshServiceNotification(
        context: Context,
        repository: KnowledgeRepository
    ) {
        val currentItem = repository.getCurrentPushItem() ?: return
        updateNotification(context, repository, currentItem)
    }

    /** 构建 QQ音乐风格 RemoteViews 通知 */
    private fun buildUpdatedNotification(
        context: Context,
        item: com.dailyknowledge.data.model.KnowledgeItem,
        totalCount: Int,
        ttsManager: TtsManager?
    ): android.app.Notification {
        val openIntent = Intent(context, com.dailyknowledge.ui.MainActivity::class.java)
        val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            android.app.PendingIntent.FLAG_UPDATE_CURRENT
        }
        val contentIntent = android.app.PendingIntent.getActivity(context, 0, openIntent, flags)

        val favLabel = if (item.isFavorite) "★ 已收藏" else "☆ 收藏"
        val ttsLabel = when {
            ttsManager == null || !ttsManager.isReady() -> "🔇 朗读"
            ttsManager.isSpeaking() -> "⏹ 停止"
            else -> "🔊 朗读"
        }
        val progressText = "第${item.indexInFile + 1}条/共${totalCount}条"

        fun buildAction(action: String): android.app.PendingIntent {
            val intent = Intent(context, NotificationActionReceiver::class.java).apply {
                this.action = action
            }
            return android.app.PendingIntent.getBroadcast(context, action.hashCode(), intent, flags)
        }

        // TTS 按钮直达 Service，比 getBroadcast 更快
        fun buildTtsAction(): android.app.PendingIntent {
            val intent = Intent(context, DailyNotificationService::class.java).apply {
                action = DailyNotificationService.ACTION_TTS
            }
            return android.app.PendingIntent.getService(
                context, DailyNotificationService.ACTION_TTS.hashCode(), intent, flags
            )
        }

        // === 折叠和展开都用完整布局，确保按钮始终可见 ===
        val contentView = android.widget.RemoteViews(context.packageName, com.dailyknowledge.R.layout.notification_daily_knowledge_expanded)
        contentView.setTextViewText(com.dailyknowledge.R.id.tv_knowledge_content, item.content)
        contentView.setTextViewText(com.dailyknowledge.R.id.tv_progress, progressText)
        contentView.setTextViewText(com.dailyknowledge.R.id.btn_prev, "◀ 上一条")
        contentView.setTextViewText(com.dailyknowledge.R.id.btn_next, "下一条 ▶")
        contentView.setTextViewText(com.dailyknowledge.R.id.btn_read_aloud, ttsLabel)
        contentView.setTextViewText(com.dailyknowledge.R.id.btn_favorite, favLabel)
        contentView.setTextViewText(com.dailyknowledge.R.id.btn_share, "📤 分享")

        // 设置按钮点击事件 — 朗读按钮用 getService 直达 Service
        contentView.setOnClickPendingIntent(com.dailyknowledge.R.id.btn_prev, buildAction(ACTION_NAV_PREV))
        contentView.setOnClickPendingIntent(com.dailyknowledge.R.id.btn_next, buildAction(ACTION_NAV_NEXT))
        contentView.setOnClickPendingIntent(com.dailyknowledge.R.id.btn_read_aloud, buildTtsAction())
        contentView.setOnClickPendingIntent(com.dailyknowledge.R.id.btn_favorite, buildAction(ACTION_FAVORITE_TOGGLE))
        contentView.setOnClickPendingIntent(com.dailyknowledge.R.id.btn_share, buildAction(ACTION_SHARE))

        return androidx.core.app.NotificationCompat.Builder(context, DailyNotificationService.CHANNEL_ID)
            .setSmallIcon(com.dailyknowledge.R.drawable.ic_notification)
            .setCustomContentView(contentView)
            .setCustomBigContentView(contentView)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setDefaults(0)
            .setSound(null)
            .setVibrate(longArrayOf(0))
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
            .setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }
}
