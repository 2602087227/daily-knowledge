package com.dailyknowledge.data.db

import androidx.room.*
import com.dailyknowledge.data.model.KnowledgeItem
import kotlinx.coroutines.flow.Flow

/**
 * 知识条目 DAO — 管理知识内容
 */
@Dao
interface KnowledgeItemDao {

    /** 获取指定文件的所有条目，按序号排序 */
    @Query("SELECT * FROM knowledge_items WHERE fileId = :fileId ORDER BY indexInFile ASC")
    fun getItemsByFile(fileId: Long): Flow<List<KnowledgeItem>>

    /** 获取指定文件的条目数量 */
    @Query("SELECT COUNT(*) FROM knowledge_items WHERE fileId = :fileId")
    suspend fun getItemCountByFile(fileId: Long): Int

    /** 获取指定文件的单个条目（按序号） */
    @Query("SELECT * FROM knowledge_items WHERE fileId = :fileId AND indexInFile = :index LIMIT 1")
    suspend fun getItemByFileAndIndex(fileId: Long, index: Int): KnowledgeItem?

    /** 按 ID 获取条目 */
    @Query("SELECT * FROM knowledge_items WHERE id = :itemId LIMIT 1")
    suspend fun getItemById(itemId: Long): KnowledgeItem?

    /** 批量插入条目 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<KnowledgeItem>)

    /** 更新条目 */
    @Update
    suspend fun updateItem(item: KnowledgeItem)

    /** 切换收藏状态 */
    @Query("UPDATE knowledge_items SET isFavorite = CASE WHEN isFavorite = 1 THEN 0 ELSE 1 END WHERE id = :itemId")
    suspend fun toggleFavorite(itemId: Long)

    /** 设为收藏 */
    @Query("UPDATE knowledge_items SET isFavorite = 1 WHERE id = :itemId")
    suspend fun setFavorite(itemId: Long)

    /** 取消收藏 */
    @Query("UPDATE knowledge_items SET isFavorite = 0 WHERE id = :itemId")
    suspend fun removeFavorite(itemId: Long)

    /** 获取所有收藏条目，按 id 倒序（近似收藏时间） */
    @Query("SELECT * FROM knowledge_items WHERE isFavorite = 1 ORDER BY id DESC")
    fun getFavoriteItems(): Flow<List<KnowledgeItem>>

    /** 搜索知识内容（模糊匹配） */
    @Query("SELECT * FROM knowledge_items WHERE content LIKE '%' || :query || '%' ORDER BY id DESC")
    fun searchItems(query: String): Flow<List<KnowledgeItem>>

    /** 删除指定文件的所有条目 */
    @Query("DELETE FROM knowledge_items WHERE fileId = :fileId")
    suspend fun deleteItemsByFile(fileId: Long)

    /** 获取指定文件的第一条 */
    @Query("SELECT * FROM knowledge_items WHERE fileId = :fileId ORDER BY indexInFile ASC LIMIT 1")
    suspend fun getFirstItemByFile(fileId: Long): KnowledgeItem?

    /** 获取指定文件的最后一条 */
    @Query("SELECT * FROM knowledge_items WHERE fileId = :fileId ORDER BY indexInFile DESC LIMIT 1")
    suspend fun getLastItemByFile(fileId: Long): KnowledgeItem?
}
