package com.dailyknowledge.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dailyknowledge.data.model.KnowledgeItem
import com.dailyknowledge.data.repository.KnowledgeRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*

/**
 * 今日知识 ViewModel — 管理当前推送的知识
 */
class TodayViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = KnowledgeRepository(application)
    private var loadJob: Job? = null

    /** 当前知识条目 */
    private val _currentItem = MutableStateFlow<KnowledgeItem?>(null)
    val currentItem: StateFlow<KnowledgeItem?> = _currentItem.asStateFlow()

    /** 加载状态 */
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** 错误消息 — 带缓冲区避免 emit 挂起 */
    private val _errorMessage = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 3,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val errorMessage: SharedFlow<String> = _errorMessage.asSharedFlow()

    /** 是否有激活的知识源 */
    private val _hasActiveSource = MutableStateFlow(false)
    val hasActiveSource: StateFlow<Boolean> = _hasActiveSource.asStateFlow()

    /** 当前激活文件的总条数 */
    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount.asStateFlow()

    /** 当前推送索引（0-based） */
    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    /** Toast 提示（一次性事件） */
    private val _toastEvent = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 2,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    init {
        loadCurrentKnowledge()
    }

    /** 加载当前推送的知识（取消上一次未完成的加载） */
    fun loadCurrentKnowledge() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _isLoading.value = true
            try {
                val item = withContext(Dispatchers.IO) {
                    repository.getDailyPushItem()
                }
                _currentItem.value = item

                val activeFile = withContext(Dispatchers.IO) {
                    repository.getActiveFile()
                }
                _hasActiveSource.value = activeFile != null
                _totalCount.value = activeFile?.knowledgeCount ?: 0
                _currentIndex.value = withContext(Dispatchers.IO) {
                    repository.getCurrentPushIndex()
                }
            } catch (e: CancellationException) {
                // 被取消，忽略
            } catch (e: Exception) {
                _toastEvent.tryEmit("加载失败: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** 切换到上一条 */
    fun moveToPrev() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                val item = withContext(Dispatchers.IO) {
                    repository.moveToPrevItem()
                }
                _currentItem.value = item
                _currentIndex.value = withContext(Dispatchers.IO) {
                    repository.getCurrentPushIndex()
                }
            } catch (e: CancellationException) {
                // ignored
            } catch (e: Exception) {
                _toastEvent.tryEmit("切换失败: ${e.message}")
            }
        }
    }

    /** 切换到下一条 */
    fun moveToNext() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                val item = withContext(Dispatchers.IO) {
                    repository.moveToNextItem()
                }
                _currentItem.value = item
                _currentIndex.value = withContext(Dispatchers.IO) {
                    repository.getCurrentPushIndex()
                }
            } catch (e: CancellationException) {
                // ignored
            } catch (e: Exception) {
                _toastEvent.tryEmit("切换失败: ${e.message}")
            }
        }
    }

    /** 切换收藏状态 — 直接更新本地状态，避免重新加载 */
    fun toggleFavorite(itemId: Long) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    repository.toggleFavorite(itemId)
                }
                // 原地更新 isFavorite 标志，不重新查询
                _currentItem.value?.let { item ->
                    if (item.id == itemId) {
                        _currentItem.value = item.copy(isFavorite = !item.isFavorite)
                    }
                }
            } catch (e: Exception) {
                _toastEvent.tryEmit("收藏失败: ${e.message}")
            }
        }
    }

    /** 分享当前知识 */
    fun shareKnowledge(content: String) {
        com.dailyknowledge.util.ShareUtil.shareKnowledge(getApplication(), content)
    }
}
