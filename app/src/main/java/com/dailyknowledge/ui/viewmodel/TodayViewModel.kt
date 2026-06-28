package com.dailyknowledge.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dailyknowledge.data.model.KnowledgeItem
import com.dailyknowledge.data.repository.KnowledgeRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 今日知识 ViewModel — 管理当前推送的知识
 */
class TodayViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = KnowledgeRepository(application)

    /** 当前知识条目 */
    private val _currentItem = MutableStateFlow<KnowledgeItem?>(null)
    val currentItem: StateFlow<KnowledgeItem?> = _currentItem.asStateFlow()

    /** 加载状态 */
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** 错误消息 */
    private val _errorMessage = MutableSharedFlow<String>()
    val errorMessage: SharedFlow<String> = _errorMessage.asSharedFlow()

    /** 是否有激活的知识源 */
    private val _hasActiveSource = MutableStateFlow(false)
    val hasActiveSource: StateFlow<Boolean> = _hasActiveSource.asStateFlow()

    init {
        loadCurrentKnowledge()
    }

    /** 加载当前推送的知识 */
    fun loadCurrentKnowledge() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // 获取今日推送的知识（如需则自动推进索引）
                val item = repository.getDailyPushItem()
                _currentItem.value = item

                // 检查是否有激活的知识源
                val activeFile = repository.getActiveFile()
                _hasActiveSource.value = activeFile != null
            } catch (e: Exception) {
                _errorMessage.emit("加载知识失败: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** 切换到上一条 */
    fun moveToPrev() {
        viewModelScope.launch {
            try {
                val item = repository.moveToPrevItem()
                _currentItem.value = item
            } catch (e: Exception) {
                _errorMessage.emit("切换失败: ${e.message}")
            }
        }
    }

    /** 切换到下一条 */
    fun moveToNext() {
        viewModelScope.launch {
            try {
                val item = repository.moveToNextItem()
                _currentItem.value = item
            } catch (e: Exception) {
                _errorMessage.emit("切换失败: ${e.message}")
            }
        }
    }

    /** 切换收藏状态 */
    fun toggleFavorite(itemId: Long) {
        viewModelScope.launch {
            try {
                repository.toggleFavorite(itemId)
                // 刷新当前条目以更新收藏状态
                loadCurrentKnowledge()
            } catch (e: Exception) {
                _errorMessage.emit("收藏操作失败: ${e.message}")
            }
        }
    }

    /** 分享当前知识 */
    fun shareKnowledge(content: String) {
        com.dailyknowledge.util.ShareUtil.shareKnowledge(getApplication(), content)
    }
}
