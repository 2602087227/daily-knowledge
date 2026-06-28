package com.dailyknowledge.ui.screen

import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dailyknowledge.data.model.KnowledgeItem
import com.dailyknowledge.ui.viewmodel.TodayViewModel
import com.dailyknowledge.util.PreferencesManager
import com.dailyknowledge.worker.DailyNotificationWorker
import java.text.SimpleDateFormat
import java.util.*

/**
 * 今日知识页 — 显示当天推送的知识
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayKnowledgeScreen(
    viewModel: TodayViewModel,
    onNavigateToImport: () -> Unit
) {
    val currentItem by viewModel.currentItem.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val hasActiveSource by viewModel.hasActiveSource.collectAsState()
    val context = LocalContext.current

    // 收集一次性事件（Toast）
    LaunchedEffect(Unit) {
        viewModel.toastEvent.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    // 首次使用引导
    if (!hasActiveSource && !isLoading) {
        EmptyStateView(onImportClick = onNavigateToImport)
        return
    }

    // 时间选择器状态（每次读取最新值，不缓存）
    val prefs = remember { PreferencesManager(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("今日知识") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    // 设置通知时间
                    IconButton(onClick = {
                        // 每次打开都读取最新保存的时间
                        val currentHour = prefs.getNotificationHour()
                        val currentMinute = prefs.getNotificationMinute()
                        TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                prefs.setNotificationTime(hour, minute)
                                DailyNotificationWorker.schedule(context)
                                Toast.makeText(
                                    context,
                                    "通知时间已设为 ${"%02d:%02d".format(hour, minute)}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            currentHour,
                            currentMinute,
                            true // is24HourView
                        ).show()
                    }) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = "设置通知时间",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        when {
            isLoading && currentItem == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            currentItem == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text("加载中…", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            }

            else -> {
                // 捕获当前条目值，避免 lambda 中延迟求值到 null
                val item = currentItem!!
                KnowledgeDetailView(
                    item = item,
                    paddingValues = paddingValues,
                    onPrev = { viewModel.moveToPrev() },
                    onNext = { viewModel.moveToNext() },
                    onToggleFavorite = { viewModel.toggleFavorite(item.id) },
                    onShare = { viewModel.shareKnowledge(item.content) }
                )
            }
        }
    }
}

/** 知识详情展示 */
@Composable
private fun KnowledgeDetailView(
    item: KnowledgeItem,
    paddingValues: PaddingValues,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShare: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 知识内容卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = SimpleDateFormat("yyyy年MM月dd日", Locale.CHINESE)
                        .format(Date()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Text(
                    text = item.content,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 18.sp,
                        lineHeight = 28.sp
                    ),
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "#${item.indexInFile + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 操作按钮行 1：上一页 / 下一页
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            FilledTonalButton(onClick = onPrev) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "上一页")
                Spacer(modifier = Modifier.width(4.dp))
                Text("上一页")
            }

            FilledTonalButton(onClick = onNext) {
                Text("下一页")
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.Default.ChevronRight, contentDescription = "下一页")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 操作按钮行 2：收藏 / 分享
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val favIcon = if (item.isFavorite) Icons.Default.Star else Icons.Default.StarBorder
            val favLabel = if (item.isFavorite) "已收藏" else "收藏"
            FilledTonalButton(onClick = onToggleFavorite) {
                Icon(favIcon, contentDescription = favLabel)
                Spacer(modifier = Modifier.width(4.dp))
                Text(favLabel)
            }

            FilledTonalButton(onClick = onShare) {
                Icon(Icons.Default.Share, contentDescription = "分享")
                Spacer(modifier = Modifier.width(4.dp))
                Text("分享")
            }
        }
    }
}

/** 空状态引导视图 */
@Composable
private fun EmptyStateView(onImportClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.MenuBook,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "暂无知识",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "首次使用，请先导入知识文件\n支持 .txt（每行一条）和 .csv（第一列）",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onImportClick) {
            Icon(Icons.Default.FileOpen, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("导入文件")
        }
    }
}
