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
            ACTION_TTS_TOGGLE -> ttsManager?.let { handleTtsToggle(context, repository, it) }
            ACTION_FAVORITE_TOGGLE -> handleFavoriteToggle(context, repository)
            ACTION_SHARE -> handleShare(context, repository)
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

    /** 朗读/停止切换 */
    private fun handleTtsToggle(
        context: Context,
        repository: KnowledgeRepository,
        ttsManager: TtsManager
    ) {
        scope.launch {
            try {
                val currentItem = repository.getCurrentPushItem()
                if (currentItem != null) {
                    val ok = ttsManager.speakOrStop(currentItem.content)
                    if (!ok) {
                        // TTS 不可用，弹 Toast 提示
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(
                                context,
                                "朗读引擎未就绪，请检查系统 TTS 设置",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
                // TTS 状态变化会通过回调自动刷新通知
            } catch (e: Exception) {
                Log.e(TAG, "TTS 操作失败", e)
            }
        }
    }

    /** 收藏/取消收藏 */
    private fun handleFavoriteToggle(context: Context, repository: KnowledgeRepository) {
        scope.launch {
            try {
                val currentItem = repository.getCurrentPushItem()
                if (currentItem != null) {
                    repository.toggleFavorite(currentItem.id)
                    // 刷新通知以更新收藏按钮状态
                    refreshServiceNotification(context, repository)
                }
            } catch (e: Exception) {
                Log.e(TAG, "收藏操作失败", e)
            }
        }
    }

    /** 分享当前知识 */
    private fun handleShare(context: Context, repository: KnowledgeRepository) {
        scope.launch {
            try {
                val currentItem = repository.getCurrentPushItem()
                if (currentItem != null) {
                    ShareUtil.shareKnowledge(context, currentItem.content)
                }
            } catch (e: Exception) {
                Log.e(TAG, "分享失败", e)
            }
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
     * 更新通知内容 — 与 Service 共享锁，防止并发 RemoteViews 更新
     */
    private suspend fun updateNotification(
        context: Context,
        repository: KnowledgeRepository,
        item: com.dailyknowledge.data.model.KnowledgeItem
    ) {
        // suspend 调用放在 synchronized 外部，避免"临界区内挂起"编译错误
        val totalCount = repository.getActiveFile()?.knowledgeCount ?: 0
        val isFavorite = item.isFavorite
        val ttsManager = DailyKnowledgeApp.getInstance().ttsManager

        // 同步 Service 的共享状态，防止 TTS 回调时回退到旧内容
        DailyNotificationService.currentNotificationItem = item

        synchronized(DailyNotificationService.notificationUpdateLock) {
            val remoteViews = android.widget.RemoteViews(
                context.packageName,
                com.dailyknowledge.R.layout.notification_daily_knowledge
            )
            remoteViews.setTextViewText(
                com.dailyknowledge.R.id.tv_knowledge_content,
                item.content
            )
            remoteViews.setTextViewText(
                com.dailyknowledge.R.id.tv_progress,
                "${item.indexInFile + 1}/$totalCount"
            )
            // 收藏按钮状态
            val favText = if (isFavorite) "★ 已收藏" else "☆ 收藏"
            remoteViews.setTextViewText(com.dailyknowledge.R.id.btn_favorite, favText)
            // TTS 按钮状态（安全处理 ttsManager 为 null 的情况）
            val ttsText = when {
                ttsManager == null || !ttsManager.isReady() -> "🔇 朗读"
                ttsManager.isSpeaking() -> "⏹ 停止"
                else -> "🔊 朗读"
            }
            remoteViews.setTextViewText(com.dailyknowledge.R.id.btn_read_aloud, ttsText)

            bindNotificationButtons(context, remoteViews)

            val notification = buildUpdatedNotification(context, remoteViews)
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

    /** 绑定通知栏按钮的广播 PendingIntent */
    private fun bindNotificationButtons(context: Context, remoteViews: android.widget.RemoteViews) {
        val actions = mapOf(
            com.dailyknowledge.R.id.btn_prev to ACTION_NAV_PREV,
            com.dailyknowledge.R.id.btn_next to ACTION_NAV_NEXT,
            com.dailyknowledge.R.id.btn_read_aloud to ACTION_TTS_TOGGLE,
            com.dailyknowledge.R.id.btn_favorite to ACTION_FAVORITE_TOGGLE,
            com.dailyknowledge.R.id.btn_share to ACTION_SHARE
        )

        val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            android.app.PendingIntent.FLAG_UPDATE_CURRENT
        }

        for ((viewId, action) in actions) {
            val intent = Intent(context, NotificationActionReceiver::class.java).apply {
                this.action = action
            }
            val pendingIntent = android.app.PendingIntent.getBroadcast(
                context, action.hashCode(), intent, flags
            )
            remoteViews.setOnClickPendingIntent(viewId, pendingIntent)
        }
    }

    /** 构建更新后的 Notification */
    private fun buildUpdatedNotification(
        context: Context,
        remoteViews: android.widget.RemoteViews
    ): android.app.Notification {
        val openIntent = Intent(context, com.dailyknowledge.ui.MainActivity::class.java)
        val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            android.app.PendingIntent.FLAG_UPDATE_CURRENT
        }
        val contentIntent = android.app.PendingIntent.getActivity(context, 0, openIntent, flags)

        return androidx.core.app.NotificationCompat.Builder(context, DailyNotificationService.CHANNEL_ID)
            .setSmallIcon(com.dailyknowledge.R.drawable.ic_notification)
            .setStyle(androidx.core.app.NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(remoteViews)
            .setCustomBigContentView(remoteViews)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
            .setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }
}
