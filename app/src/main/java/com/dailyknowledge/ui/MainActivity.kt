package com.dailyknowledge.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.dailyknowledge.service.DailyNotificationService
import com.dailyknowledge.ui.navigation.AppNavigation
import com.dailyknowledge.ui.theme.DailyKnowledgeTheme

/**
 * 主 Activity — 应用唯一入口
 * 处理通知权限请求并启动前台服务
 */
class MainActivity : ComponentActivity() {

    // 通知权限请求 Launcher (Android 13+)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startNotificationService()
        } else {
            Toast.makeText(
                this,
                "通知权限被拒绝，通知栏将无法显示每日知识",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            DailyKnowledgeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }

        // 检查并请求通知权限
        checkAndRequestNotificationPermission()

        // 权限已有时直接启动服务
        if (hasNotificationPermission()) {
            startNotificationService()
        }
    }

    /**
     * 检查并请求通知权限
     * Android 13+ 需要 POST_NOTIFICATIONS 权限
     */
    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // 权限已授予
                }

                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    // 用户曾拒绝，可显示解释理由
                    Toast.makeText(
                        this,
                        "通知权限用于在通知栏显示每日知识，请在弹窗中允许",
                        Toast.LENGTH_LONG
                    ).show()
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }

                else -> {
                    // 首次请求
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    /**
     * 检查是否拥有通知权限
     */
    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Android 12 及以下无需运行时权限
        }
    }

    /**
     * 启动前台通知服务
     */
    private fun startNotificationService() {
        DailyNotificationService.start(this)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // 备用：如果用户从设置页面返回后权限被授予，重新启动服务
        if (requestCode == REQUEST_NOTIFICATION_SETTINGS) {
            if (hasNotificationPermission()) {
                startNotificationService()
            }
        }
    }

    companion object {
        private const val REQUEST_NOTIFICATION_SETTINGS = 101
    }
}
