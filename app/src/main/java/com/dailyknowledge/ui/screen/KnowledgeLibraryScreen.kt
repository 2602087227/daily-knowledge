package com.dailyknowledge.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dailyknowledge.data.model.KnowledgeFile
import com.dailyknowledge.data.model.KnowledgeItem
import com.dailyknowledge.ui.viewmodel.LibraryViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * 知识库页 — 文件列表 + 知识条目列表
 * 支持文件管理（激活、删除）和知识浏览
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeLibraryScreen(
    viewModel: LibraryViewModel,
    onNavigateToImport: () -> Unit
) {
    val allFiles by viewModel.allFiles.collectAsState()
    val currentItems by viewModel.currentItems.collectAsState()
    val selectedFileId by viewModel.selectedFileId.collectAsState()
    val importState by viewModel.importState.collectAsState()
    val deleteConfirmFileId by viewModel.deleteConfirmFileId.collectAsState()

    // 文件选择器
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            // 从 ContentResolver 获取文件名
            val cursor = viewModel.getApplication<android.app.Application>()
                .contentResolver.query(uri, null, null, null, null)
            var fileName = "unknown_file"
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        fileName = it.getString(nameIndex)
                    }
                }
            }
            viewModel.importFile(uri, fileName)
        }
    }

    // 处理删除确认对话框
    deleteConfirmFileId?.let { fileId ->
        val file = allFiles.find { it.id == fileId }
        AlertDialog(
            onDismissRequest = { viewModel.cancelDelete() },
            title = { Text("确认删除") },
            text = { Text("确定要删除「${file?.fileName ?: "此文件"}」及其所有知识条目吗？") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmDeleteFile() },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDelete() }) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("知识库") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    IconButton(onClick = {
                        filePickerLauncher.launch(arrayOf(
                            "text/plain",
                            "text/csv",
                            "text/comma-separated-values",
                            "*/*"
                        ))
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "导入文件", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            )
        }
    ) { paddingValues ->
        if (allFiles.isEmpty()) {
            // 空状态
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Description,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("还没有导入任何知识文件", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = {
                    filePickerLauncher.launch(arrayOf(
                        "text/plain",
                        "text/csv",
                        "text/comma-separated-values",
                        "*/*"
                    ))
                }) {
                    Icon(Icons.Default.FileOpen, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("导入文件")
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // 文件列表（横向滚动标签）
                FileSelector(
                    files = allFiles,
                    selectedFileId = selectedFileId,
                    onFileSelected = { viewModel.selectFile(it.id) },
                    onActivate = { viewModel.setActiveFile(it.id) },
                    onDelete = { viewModel.requestDeleteFile(it.id) }
                )

                // 导入状态指示
                when (val state = importState) {
                    is LibraryViewModel.ImportState.Loading -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    else -> {}
                }

                // 知识条目列表
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    items(currentItems, key = { it.id }) { item ->
                        KnowledgeItemCard(
                            item = item,
                            onToggleFavorite = { viewModel.toggleFavorite(item.id) },
                            onShare = { viewModel.shareKnowledge(item.content) }
                        )
                    }
                }
            }
        }
    }
}

/** 文件选择器 — 横向排列的文件标签 */
@Composable
private fun FileSelector(
    files: List<KnowledgeFile>,
    selectedFileId: Long?,
    onFileSelected: (KnowledgeFile) -> Unit,
    onActivate: (KnowledgeFile) -> Unit,
    onDelete: (KnowledgeFile) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        files.forEach { file ->
            val isSelected = file.id == selectedFileId
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { onFileSelected(file) },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected)
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    else
                        MaterialTheme.colorScheme.surface
                ),
                border = if (isSelected) {
                    androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                } else null
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = file.fileName,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (file.isActive) {
                                Spacer(modifier = Modifier.width(8.dp))
                                AssistChip(
                                    onClick = {},
                                    label = { Text("使用中", fontSize = MaterialTheme.typography.labelSmall.fontSize) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${dateFormat.format(Date(file.importTime))} · ${file.knowledgeCount}条知识",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )

                        Row {
                            // 若未激活则显示"设为当前"按钮
                            if (!file.isActive) {
                                TextButton(
                                    onClick = { onActivate(file) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Text("设为当前", fontSize = MaterialTheme.typography.labelSmall.fontSize)
                                }
                            }

                            // 删除按钮
                            IconButton(
                                onClick = { onDelete(file) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "删除",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 知识条目卡片 */
@Composable
private fun KnowledgeItemCard(
    item: KnowledgeItem,
    onToggleFavorite: () -> Unit,
    onShare: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 序号
            Text(
                text = "#${item.indexInFile + 1}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 内容
            Text(
                text = item.content,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 操作按钮
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (item.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "收藏",
                        tint = if (item.isFavorite)
                            com.dailyknowledge.ui.theme.FavoriteActive
                        else
                            com.dailyknowledge.ui.theme.FavoriteInactive,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onShare, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Share, contentDescription = "分享", modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
