package com.dailyknowledge.data.repository

import android.content.Context
import android.net.Uri
import com.dailyknowledge.data.db.AppDatabase
import com.dailyknowledge.data.model.KnowledgeFile
import com.dailyknowledge.data.model.KnowledgeItem
import com.dailyknowledge.util.PreferencesManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

/**
 * 知识仓库 — 统一数据访问层
 * 负责文件管理、知识 CRUD、推送索引状态
 */
class KnowledgeRepository(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val fileDao = db.knowledgeFileDao()
    private val itemDao = db.knowledgeItemDao()
    private val prefs = PreferencesManager(context)

    /** 每日推送互斥锁 — 防止并发调用导致索引多次推进 */
    private val dailyPushMutex = Mutex()

    // ==================== 文件管理 ====================

    /** 获取所有文件列表 */
    fun getAllFiles(): Flow<List<KnowledgeFile>> = fileDao.getAllFiles()

    /** 获取当前激活的文件 */
    suspend fun getActiveFile(): KnowledgeFile? = fileDao.getActiveFile()

    /** 流式观察激活文件 */
    fun observeActiveFile(): Flow<KnowledgeFile?> = fileDao.observeActiveFile()

    /**
     * 导入文件
     * @param uri 用户通过文件选择器选中的文件 URI
     * @param fileName 文件名
     * @return 导入结果：成功返回文件对象，失败抛异常
     */
    suspend fun importFile(uri: Uri, fileName: String): KnowledgeFile {
        // 1. 复制文件到应用私有目录
        val privateDir = File(context.filesDir, "knowledge_files")
        if (!privateDir.exists()) privateDir.mkdirs()

        val destFile = File(privateDir, "${System.currentTimeMillis()}_$fileName")
        context.contentResolver.openInputStream(uri)?.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw Exception("无法读取文件")

        // 2. 解析文件内容
        val items = parseFile(destFile, fileName)
        if (items.isEmpty()) {
            destFile.delete()
            throw Exception("文件中无有效内容")
        }

        // 3. 插入文件记录
        val knowledgeFile = KnowledgeFile(
            fileName = fileName,
            filePath = destFile.absolutePath,
            importTime = System.currentTimeMillis(),
            isActive = false,
            knowledgeCount = items.size
        )
        val fileId = fileDao.insertFile(knowledgeFile)

        // 4. 插入知识条目
        val knowledgeItems = items.mapIndexed { index, content ->
            KnowledgeItem(
                fileId = fileId,
                content = content,
                indexInFile = index,
                isFavorite = false
            )
        }
        itemDao.insertItems(knowledgeItems)

        // 5. 激活为当前知识源
        fileDao.deactivateAll()
        fileDao.activateFile(fileId)

        // 6. 重置推送索引并标记今天已推送，防止 getDailyPushItem 再次前进
        prefs.resetPushIndex()
        prefs.setLastPushDate(SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Date()))
        prefs.setActiveFileId(fileId)

        return knowledgeFile.copy(id = fileId, isActive = true)
    }

    /**
     * 解析文件内容
     * .txt：每行一条知识，跳过空行
     * .csv：读取第一列，跳过表头行（若第一行包含"知识"、"内容"等关键词）
     */
    private fun parseFile(file: File, fileName: String): List<String> {
        val items = mutableListOf<String>()

        BufferedReader(InputStreamReader(file.inputStream(), Charsets.UTF_8)).use { reader ->
            val isCsv = fileName.lowercase(Locale.ROOT).endsWith(".csv")
            var isFirstLine = true

            reader.forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty()) return@forEachLine

                if (isCsv) {
                    // CSV：取第一列
                    val columns = parseCsvLine(trimmed)
                    val firstColumn = columns.firstOrNull()?.trim() ?: ""
                    if (firstColumn.isEmpty()) return@forEachLine

                    // 跳过疑似表头的第一行
                    if (isFirstLine && isHeaderLine(firstColumn)) {
                        isFirstLine = false
                        return@forEachLine
                    }
                    items.add(firstColumn)
                } else {
                    // TXT：整行作为一条知识
                    items.add(trimmed)
                }
                isFirstLine = false
            }
        }

        return items
    }

    /** 简单的 CSV 行解析（处理引号内的逗号及转义引号 ""） */
    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val char = line[i]
            when {
                char == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    // 转义的双引号 ""
                    current.append('"')
                    i += 2
                    continue
                }
                char == '"' -> {
                    inQuotes = !inQuotes
                }
                char == ',' && !inQuotes -> {
                    result.add(current.toString())
                    current.clear()
                }
                else -> current.append(char)
            }
            i++
        }
        result.add(current.toString())
        return result
    }

    /** 判断是否为表头行 */
    private fun isHeaderLine(text: String): Boolean {
        val headers = listOf("知识", "内容", "content", "标题", "title", "text", "名称", "name")
        return headers.any { text.contains(it, ignoreCase = true) }
    }

    /** 设置当前知识源 */
    suspend fun setActiveFile(fileId: Long) {
        fileDao.deactivateAll()
        fileDao.activateFile(fileId)
        prefs.resetPushIndex()
        prefs.setActiveFileId(fileId)
    }

    /** 删除文件及其所有知识条目（Room CASCADE 自动删除关联条目） */
    suspend fun deleteFile(fileId: Long) {
        // 清理激活状态
        val file = fileDao.getFileById(fileId)
        if (file?.isActive == true) {
            prefs.resetPushIndex()
            prefs.clearActiveFileId()
        }
        fileDao.deleteFileById(fileId)
    }

    // ==================== 知识条目 ====================

    /** 获取指定文件的知识条目 */
    fun getItemsByFile(fileId: Long): Flow<List<KnowledgeItem>> =
        itemDao.getItemsByFile(fileId)

    /** 获取当前推送索引 */
    suspend fun getCurrentPushIndex(): Int = prefs.getCurrentPushIndex()

    /** 获取当前推送的知识条目 */
    suspend fun getCurrentPushItem(): KnowledgeItem? {
        val activeFile = fileDao.getActiveFile() ?: return null
        val index = prefs.getCurrentPushIndex()
        // 循环处理
        val actualIndex = if (activeFile.knowledgeCount > 0) {
            index % activeFile.knowledgeCount
        } else {
            return null
        }
        return itemDao.getItemByFileAndIndex(activeFile.id, actualIndex)
    }

    /** 切换到下一条知识（推进推送索引） */
    suspend fun moveToNextItem(): KnowledgeItem? {
        val activeFile = fileDao.getActiveFile() ?: return null
        if (activeFile.knowledgeCount == 0) return null

        val currentIndex = prefs.getCurrentPushIndex()
        val nextIndex = (currentIndex + 1) % activeFile.knowledgeCount
        prefs.setCurrentPushIndex(nextIndex)

        return itemDao.getItemByFileAndIndex(activeFile.id, nextIndex)
    }

    /** 切换到上一条知识 */
    suspend fun moveToPrevItem(): KnowledgeItem? {
        val activeFile = fileDao.getActiveFile() ?: return null
        if (activeFile.knowledgeCount == 0) return null

        val currentIndex = prefs.getCurrentPushIndex()
        val prevIndex = if (currentIndex == 0) {
            activeFile.knowledgeCount - 1
        } else {
            currentIndex - 1
        }
        prefs.setCurrentPushIndex(prevIndex)

        return itemDao.getItemByFileAndIndex(activeFile.id, prevIndex)
    }

    /** 每日推送：若今天还未推送则推进索引（互斥保护，防止并发推进多次） */
    suspend fun getDailyPushItem(): KnowledgeItem? = dailyPushMutex.withLock {
        val today = SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Date())
        val lastDate = prefs.getLastPushDate()

        if (lastDate != today) {
            // 今天还没推送过，推进索引
            prefs.setLastPushDate(today)
            return@withLock moveToNextItem()
        }

        // 今天已推送过，返回当前索引的知识
        return@withLock getCurrentPushItem()
    }

    /** 按 ID 获取条目 */
    suspend fun getItemById(itemId: Long): KnowledgeItem? =
        itemDao.getItemById(itemId)

    /** 切换收藏状态 */
    suspend fun toggleFavorite(itemId: Long) {
        itemDao.toggleFavorite(itemId)
    }

    /** 获取收藏列表 */
    fun getFavoriteItems(): Flow<List<KnowledgeItem>> =
        itemDao.getFavoriteItems()

    /** 搜索知识 */
    fun searchItems(query: String): Flow<List<KnowledgeItem>> =
        itemDao.searchItems(query)
}
