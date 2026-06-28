package com.dailyknowledge.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dailyknowledge.data.model.KnowledgeFile
import com.dailyknowledge.data.model.KnowledgeItem
import com.dailyknowledge.data.repository.KnowledgeRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 知识库 ViewModel — 管理文件列表和知识列表
 */
class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = KnowledgeRepository(application)

    /** 所有知识文件 */
    val allFiles: StateFlow<List<KnowledgeFile>> =
        repository.getAllFiles()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 当前选中的文件下的知识条目 */
    private val _currentItems = MutableStateFlow<List<KnowledgeItem>>(emptyList())
    val currentItems: StateFlow<List<KnowledgeItem>> = _currentItems.asStateFlow()

    /** 当前选中的文件 ID */
    private val _selectedFileId = MutableStateFlow<Long?>(null)
    val selectedFileId: StateFlow<Long?> = _selectedFileId.asStateFlow()

    /** 导入状态 */
    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    /** 提示消息 */
    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    /** 文件删除确认 */
    private val _deleteConfirmFileId = MutableStateFlow<Long?>(null)
    val deleteConfirmFileId: StateFlow<Long?> = _deleteConfirmFileId.asStateFlow()

    init {
        // 默认选中第一个文件（通常是激活的）
        viewModelScope.launch {
            allFiles.first().let { files ->
                val activeFile = files.firstOrNull { it.isActive } ?: files.firstOrNull()
                if (activeFile != null) {
                    selectFile(activeFile.id)
                }
            }
        }
    }

    /** 选择文件并加载其知识条目 */
    fun selectFile(fileId: Long) {
        _selectedFileId.value = fileId
        viewModelScope.launch {
            repository.getItemsByFile(fileId)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
                .collect { items ->
                    _currentItems.value = items
                }
        }
    }

    /** 导入文件 */
    fun importFile(uri: Uri, fileName: String) {
        viewModelScope.launch {
            _importState.value = ImportState.Loading
            try {
                val file = repository.importFile(uri, fileName)
                _importState.value = ImportState.Success(file)
                _toastMessage.emit("导入成功：${fileName}（${file.knowledgeCount}条知识）")
                // 自动选中新导入的文件
                selectFile(file.id)
            } catch (e: Exception) {
                _importState.value = ImportState.Error(e.message ?: "导入失败")
                _toastMessage.emit("导入失败：${e.message}")
            }
        }
    }

    /** 设为当前知识源 */
    fun setActiveFile(fileId: Long) {
        viewModelScope.launch {
            try {
                repository.setActiveFile(fileId)
                _toastMessage.emit("已设为当前知识源")
            } catch (e: Exception) {
                _toastMessage.emit("设置失败：${e.message}")
            }
        }
    }

    /** 请求删除文件确认 */
    fun requestDeleteFile(fileId: Long) {
        _deleteConfirmFileId.value = fileId
    }

    /** 取消删除 */
    fun cancelDelete() {
        _deleteConfirmFileId.value = null
    }

    /** 确认删除文件 */
    fun confirmDeleteFile() {
        val fileId = _deleteConfirmFileId.value ?: return
        viewModelScope.launch {
            try {
                repository.deleteFile(fileId)
                _toastMessage.emit("已删除")
                _deleteConfirmFileId.value = null
                // 如果删除的是当前选中的文件，切换到第一个
                if (_selectedFileId.value == fileId) {
                    val remaining = allFiles.value.filter { it.id != fileId }
                    if (remaining.isNotEmpty()) {
                        selectFile(remaining.first().id)
                    } else {
                        _selectedFileId.value = null
                        _currentItems.value = emptyList()
                    }
                }
            } catch (e: Exception) {
                _toastMessage.emit("删除失败：${e.message}")
            }
        }
    }

    /** 切换收藏 */
    fun toggleFavorite(itemId: Long) {
        viewModelScope.launch {
            repository.toggleFavorite(itemId)
        }
    }

    /** 分享知识 */
    fun shareKnowledge(content: String) {
        com.dailyknowledge.util.ShareUtil.shareKnowledge(getApplication(), content)
    }

    /** 清除导入状态 */
    fun clearImportState() {
        _importState.value = ImportState.Idle
    }

    /** 导入状态密封类 */
    sealed class ImportState {
        data object Idle : ImportState()
        data object Loading : ImportState()
        data class Success(val file: KnowledgeFile) : ImportState()
        data class Error(val message: String) : ImportState()
    }
}
