package com.dailyknowledge.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dailyknowledge.data.model.KnowledgeItem
import com.dailyknowledge.data.repository.KnowledgeRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 搜索 ViewModel — 模糊搜索知识条目
 */
class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = KnowledgeRepository(application)

    /** 搜索输入文本 */
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** 搜索结果 */
    private val _searchResults = MutableStateFlow<List<KnowledgeItem>>(emptyList())
    val searchResults: StateFlow<List<KnowledgeItem>> = _searchResults.asStateFlow()

    /** 是否正在搜索 */
    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    /** 是否有搜索过 */
    private val _hasSearched = MutableStateFlow(false)
    val hasSearched: StateFlow<Boolean> = _hasSearched.asStateFlow()

    private var searchJob: Job? = null

    /** 更新搜索查询（带 300ms 防抖） */
    fun onQueryChanged(newQuery: String) {
        _query.value = newQuery
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300) // 防抖
            performSearch(newQuery)
        }
    }

    /** 执行搜索 */
    private suspend fun performSearch(query: String) {
        if (query.trim().isEmpty()) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            _hasSearched.value = false
            return
        }

        _isSearching.value = true
        _hasSearched.value = true

        repository.searchItems(query.trim())
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
            .collect { results ->
                _searchResults.value = results
                _isSearching.value = false
            }
    }

    /** 切换收藏 */
    fun toggleFavorite(itemId: Long) {
        viewModelScope.launch {
            repository.toggleFavorite(itemId)
            // 重新搜索以刷新列表
            performSearch(_query.value)
        }
    }

    /** 分享知识 */
    fun shareKnowledge(content: String) {
        com.dailyknowledge.util.ShareUtil.shareKnowledge(getApplication(), content)
    }

    /** 清除搜索 */
    fun clearSearch() {
        _query.value = ""
        _searchResults.value = emptyList()
        _hasSearched.value = false
        _isSearching.value = false
        searchJob?.cancel()
    }
}
