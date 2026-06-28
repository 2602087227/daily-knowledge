package com.dailyknowledge.data.db

import androidx.room.*
import com.dailyknowledge.data.model.KnowledgeFile
import kotlinx.coroutines.flow.Flow

/**
 * 知识文件 DAO — 管理导入的知识源文件
 */
@Dao
interface KnowledgeFileDao {

    /** 获取所有文件，按导入时间倒序 */
    @Query("SELECT * FROM knowledge_files ORDER BY importTime DESC")
    fun getAllFiles(): Flow<List<KnowledgeFile>>

    /** 获取当前激活的文件 */
    @Query("SELECT * FROM knowledge_files WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveFile(): KnowledgeFile?

    /** 流式观察当前激活的文件 */
    @Query("SELECT * FROM knowledge_files WHERE isActive = 1 LIMIT 1")
    fun observeActiveFile(): Flow<KnowledgeFile?>

    /** 按 ID 获取文件 */
    @Query("SELECT * FROM knowledge_files WHERE id = :fileId")
    suspend fun getFileById(fileId: Long): KnowledgeFile?

    /** 插入新文件 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: KnowledgeFile): Long

    /** 更新文件信息 */
    @Update
    suspend fun updateFile(file: KnowledgeFile)

    /** 将所有文件设为非激活 */
    @Query("UPDATE knowledge_files SET isActive = 0")
    suspend fun deactivateAll()

    /** 激活指定文件 */
    @Query("UPDATE knowledge_files SET isActive = 1 WHERE id = :fileId")
    suspend fun activateFile(fileId: Long)

    /** 删除文件 */
    @Delete
    suspend fun deleteFile(file: KnowledgeFile)

    /** 按 ID 删除文件 */
    @Query("DELETE FROM knowledge_files WHERE id = :fileId")
    suspend fun deleteFileById(fileId: Long)
}
