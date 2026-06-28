package com.dailyknowledge.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dailyknowledge.ui.theme.FavoriteActive
import com.dailyknowledge.ui.viewmodel.FavoritesViewModel

/**
 * 收藏页 — 显示已收藏的知识条目列表
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(viewModel: FavoritesViewModel) {
    val favoriteItems by viewModel.favoriteItems.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("收藏") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        if (favoriteItems.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.StarBorder,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "还没有收藏任何知识",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "在通知栏或知识库中点击 ☆ 即可收藏",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(favoriteItems, key = { it.id }) { item ->
                    FavoriteItemCard(
                        item = item,
                        onRemoveFavorite = { viewModel.removeFavorite(item.id) },
                        onShare = { viewModel.shareKnowledge(item.content) }
                    )
                }
            }
        }
    }
}

/** 收藏条目卡片 */
@Composable
private fun FavoriteItemCard(
    item: com.dailyknowledge.data.model.KnowledgeItem,
    onRemoveFavorite: () -> Unit,
    onShare: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // 收藏图标
            Icon(
                Icons.Default.Star,
                contentDescription = "已收藏",
                tint = FavoriteActive,
                modifier = Modifier
                    .padding(end = 12.dp, top = 2.dp)
                    .size(20.dp)
            )

            // 内容区域
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.content,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 取消收藏
                    TextButton(
                        onClick = onRemoveFavorite,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("取消收藏", style = MaterialTheme.typography.labelSmall)
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // 分享
                    TextButton(
                        onClick = onShare,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("分享", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
