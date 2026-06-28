package com.dailyknowledge.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 知识文件表 — 记录导入的知识源文件
 */
@Entity(tableName = "knowledge_files")
data class KnowledgeFile(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 文件名（含扩展名） */
    val fileName: String,

    /** 文件在应用私有目录的路径 */
    val filePath: String,

    /** 导入时间戳（毫秒） */
    val importTime: Long = System.currentTimeMillis(),

    /** 是否为当前激活的知识源 */
    val isActive: Boolean = false,

    /** 文件包含的知识条数 */
    val knowledgeCount: Int = 0
)
