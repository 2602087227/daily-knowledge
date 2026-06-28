package com.dailyknowledge.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dailyknowledge.data.model.KnowledgeItem
import com.dailyknowledge.data.repository.KnowledgeRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 收藏 ViewModel — 管理收藏列表
 */
class FavoritesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = KnowledgeRepository(application)

    /** 收藏列表 */
    val favoriteItems: StateFlow<List<KnowledgeItem>> =
        repository.getFavoriteItems()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 提示消息 */
    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    /** 取消收藏 */
    fun removeFavorite(itemId: Long) {
        viewModelScope.launch {
            try {
                repository.toggleFavorite(itemId)
                _toastMessage.emit("已取消收藏")
            } catch (e: Exception) {
                _toastMessage.emit("操作失败：${e.message}")
            }
        }
    }

    /** 分享知识 */
    fun shareKnowledge(content: String) {
        com.dailyknowledge.util.ShareUtil.shareKnowledge(getApplication(), content)
    }
}
