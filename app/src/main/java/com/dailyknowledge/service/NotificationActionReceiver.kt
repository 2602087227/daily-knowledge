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
 * - ACTION_CLOSE：关闭通知（停止前台服务）
 * - ACTION_REFRESH：从外部触发刷新通知内容
 */
class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_NAV_PREV = "com.dailyknowledge.action.NAV_PREV"
        const val ACTION_NAV_NEXT = "com.dailyknowledge.action.NAV_NEXT"
        const val ACTION_TTS_TOGGLE = "com.dailyknowledge.action.TTS_TOGGLE"
        const val ACTION_FAVORITE_TOGGLE = "com.dailyknowledge.action.FAVORITE_TOGGLE"
        const val ACTION_SHARE = "com.dailyknowledge.action.SHARE"
        const val ACTION_CLOSE = "com.dailyknowledge.action.CLOSE"
        const val ACTION_REFRESH = "com.dailyknowledge.action.REFRESH"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val repository = KnowledgeRepository(context)
        // 使用 Application 级 TTS 单例，确保已初始化
        val ttsManager = DailyKnowledgeApp.getInstance().ttsManager

        when (action) {
            ACTION_NAV_PREV -> handleNavPrev(context, repository)
            ACTION_NAV_NEXT -> handleNavNext(context, repository)
            ACTION_TTS_TOGGLE -> handleTtsToggle(context, repository, ttsManager)
            ACTION_FAVORITE_TOGGLE -> handleFavoriteToggle(context, repository)
            ACTION_SHARE -> handleShare(context, repository)
            ACTION_CLOSE -> handleClose(context)
            ACTION_REFRESH -> handleRefresh(context, repository)
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
                    ttsManager.speakOrStop(currentItem.content)
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

    /** 关闭通知（停止前台服务） */
    private fun handleClose(context: Context) {
        DailyNotificationService.stop(context)
    }

    /** 刷新通知内容（由 WorkManager 等外部触发） */
    private fun handleRefresh(context: Context, repository: KnowledgeRepository) {
        scope.launch {
            try {
                val dailyItem = repository.getDailyPushItem()
                if (dailyItem != null) {
                    updateNotification(context, repository, dailyItem)
                }
            } catch (e: Exception) {
                Log.e(TAG, "刷新通知失败", e)
            }
        }
    }

    /**
     * 更新通知内容 — 通过发送隐式广播让 Service 刷新
     * 如果 Service 未运行则启动它
     */
    private suspend fun updateNotification(
        context: Context,
        repository: KnowledgeRepository,
        item: com.dailyknowledge.data.model.KnowledgeItem
    ) {
        // 直接通过 NotificationManager 更新 RemoteViews
        val remoteViews = android.widget.RemoteViews(
            context.packageName,
            com.dailyknowledge.R.layout.notification_daily_knowledge
        )
        remoteViews.setTextViewText(
            com.dailyknowledge.R.id.tv_knowledge_content,
            item.content
        )

        // 更新收藏按钮
        val isFavorite = item.isFavorite
        val favText = if (isFavorite) "★ 已收藏" else "☆ 收藏"
        remoteViews.setTextViewText(com.dailyknowledge.R.id.btn_favorite, favText)

        // 绑定按钮事件
        bindNotificationButtons(context, remoteViews)

        // 构建并发送通知
        val notification = buildUpdatedNotification(context, remoteViews)
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(DailyNotificationService.NOTIFICATION_ID, notification)
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
            com.dailyknowledge.R.id.btn_share to ACTION_SHARE,
            com.dailyknowledge.R.id.btn_close to ACTION_CLOSE
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

    companion object {
        private const val TAG = "NotificationReceiver"
    }
}
