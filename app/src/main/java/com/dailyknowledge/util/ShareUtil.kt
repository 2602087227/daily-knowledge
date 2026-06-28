package com.dailyknowledge.util

import android.content.Context
import android.content.Intent

/**
 * 分享工具 — 调起系统分享
 */
object ShareUtil {

    /**
     * 分享知识文本
     * @param context 上下文
     * @param content 知识内容
     */
    fun shareKnowledge(context: Context, content: String) {
        val shareText = buildString {
            append(content)
            append("\n\n")
            append("——来自每日小知识App")
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, "分享知识")
        }

        val chooser = Intent.createChooser(intent, "分享到")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
