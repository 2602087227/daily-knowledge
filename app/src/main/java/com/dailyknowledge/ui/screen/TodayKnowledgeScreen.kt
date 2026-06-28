package com.dailyknowledge.ui.screen

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dailyknowledge.data.model.KnowledgeItem
import com.dailyknowledge.ui.viewmodel.TodayViewModel

/**
 * 今日知识页 — 显示当天推送的知识
 * 包含：内容展示、导航按钮（上/下一条）、朗读、收藏、分享
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

    LaunchedEffect(Unit) {
        viewModel.loadCurrentKnowledge()
    }

    // 首次使用引导
    if (!hasActiveSource) {
        EmptyStateView(onImportClick = onNavigateToImport)
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("今日知识") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (currentItem == null) {
            EmptyStateView(onImportClick = onNavigateToImport)
        } else {
            KnowledgeDetailView(
                item = currentItem!!,
                paddingValues = paddingValues,
                onPrev = { viewModel.moveToPrev() },
                onNext = { viewModel.moveToNext() },
                onToggleFavorite = { viewModel.toggleFavorite(currentItem!!.id) },
                onShare = { viewModel.shareKnowledge(currentItem!!.content) }
            )
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
                // 日期标签
                Text(
                    text = java.text.SimpleDateFormat("yyyy年MM月dd日", java.util.Locale.CHINESE)
                        .format(java.util.Date()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // 知识内容
                Text(
                    text = item.content,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 18.sp,
                        lineHeight = 28.sp
                    ),
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )

                // 来源文件信息
                Text(
                    text = "#${item.indexInFile + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 操作按钮行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // 上一页
            FilledTonalButton(onClick = onPrev) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "上一页")
                Spacer(modifier = Modifier.width(4.dp))
                Text("上一页")
            }

            // 下一页
            FilledTonalButton(onClick = onNext) {
                Text("下一页")
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.Default.ChevronRight, contentDescription = "下一页")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // 收藏
            val favIcon = if (item.isFavorite) Icons.Default.Star else Icons.Default.StarBorder
            val favLabel = if (item.isFavorite) "已收藏" else "收藏"
            FilledTonalButton(onClick = onToggleFavorite) {
                Icon(favIcon, contentDescription = favLabel)
                Spacer(modifier = Modifier.width(4.dp))
                Text(favLabel)
            }

            // 分享
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
