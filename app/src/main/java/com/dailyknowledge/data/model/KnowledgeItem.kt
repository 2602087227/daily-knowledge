package com.dailyknowledge.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 知识条目表 — 文件中的每一条知识
 */
@Entity(
    tableName = "knowledge_items",
    foreignKeys = [
        ForeignKey(
            entity = KnowledgeFile::class,
            parentColumns = ["id"],
            childColumns = ["fileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("fileId"),
        Index("isFavorite")
    ]
)
data class KnowledgeItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 所属文件 ID */
    val fileId: Long,

    /** 知识文本内容 */
    val content: String,

    /** 在原始文件中的行号/序号（从0开始） */
    val indexInFile: Int,

    /** 是否已收藏 */
    val isFavorite: Boolean = false
)
