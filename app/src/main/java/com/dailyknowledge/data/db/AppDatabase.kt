package com.dailyknowledge.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.dailyknowledge.data.model.KnowledgeFile
import com.dailyknowledge.data.model.KnowledgeItem

/**
 * Room 数据库 — 主数据库定义
 */
@Database(
    entities = [KnowledgeFile::class, KnowledgeItem::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun knowledgeFileDao(): KnowledgeFileDao
    abstract fun knowledgeItemDao(): KnowledgeItemDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * 获取数据库单例
         */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "daily_knowledge.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
