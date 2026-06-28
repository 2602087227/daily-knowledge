package com.dailyknowledge.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.dailyknowledge.service.DailyNotificationService
import java.util.concurrent.TimeUnit

/**
 * WorkManager Worker — 每天早上 8:00 自动刷新通知内容
 *
 * 使用 PeriodicWorkRequest 实现定期任务。
 * 注意：PeriodicWorkRequest 的最小间隔是 15 分钟，
 * 我们使用每日周期 + 约束条件来近似每天早上 8:00 执行。
 */
class DailyNotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "开始每日推送任务")

            // 通过广播触发通知刷新
            DailyNotificationService.sendRefreshBroadcast(applicationContext)

            Log.d(TAG, "每日推送任务完成")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "每日推送任务失败", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "DailyNotificationWorker"
        private const val WORK_NAME = "daily_notification_work"

        /**
         * 调度每日通知任务
         * 每天早上 8:00 左右执行
         */
        fun schedule(context: Context) {
            // 计算距离明天早上 8:00 的延迟时间
            val now = java.util.Calendar.getInstance()
            val targetTime = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 8)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
                // 如果今天 8:00 已过，设为明天 8:00
                if (before(now)) {
                    add(java.util.Calendar.DAY_OF_YEAR, 1)
                }
            }

            val initialDelay = targetTime.timeInMillis - now.timeInMillis

            val dailyWorkRequest = PeriodicWorkRequestBuilder<DailyNotificationWorker>(
                1, TimeUnit.DAYS       // 每天执行一次
            )
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    10, TimeUnit.MINUTES
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,   // 如果已存在则更新
                dailyWorkRequest
            )

            Log.d(TAG, "已调度每日推送任务，初始延迟: ${initialDelay / 1000 / 60} 分钟")
        }

        /**
         * 取消每日通知任务
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
