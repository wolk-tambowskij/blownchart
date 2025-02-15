package app.lawnchair.data.folder.model

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lawnchair.data.folder.service.FolderService
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.FolderInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FolderViewModel(context: Context) : ViewModel() {

    private val repository = FolderService.INSTANCE.get(context)

    private val _folders = MutableStateFlow<List<FolderInfo>>(emptyList())
    val folders: StateFlow<List<FolderInfo>> = _folders.asStateFlow()

    private val _foldersMutable = MutableLiveData<List<FolderInfo>>()
    val foldersMutable: LiveData<List<FolderInfo>> = _foldersMutable

    private val _items = MutableStateFlow<Set<String>>(setOf())
    val items: StateFlow<Set<String>> = _items.asStateFlow()

    private val _folderInfo = MutableStateFlow<FolderInfo?>(null)
    val folderInfo = _folderInfo.asStateFlow()

    private val mutex = Mutex()

    init {
        viewModelScope.launch {
            mutex.withLock {
                loadFolders()
            }
        }
    }

    fun refreshFolders() {
        viewModelScope.launch {
            mutex.withLock {
                loadFolders()
            }
        }
    }

    fun setItems(id: Int) {
        viewModelScope.launch {
            val items = repository.getItems(id)
            _items.value = items
        }
    }

    fun setFolderInfo(folderInfoId: Int, hasId: Boolean) {
        viewModelScope.launch {
            _folderInfo.value = repository.getFolderInfo(folderInfoId, hasId)
        }
    }

    fun updateFolderInfo(folderInfo: FolderInfo, hide: Boolean) {
        viewModelScope.launch {
            repository.updateFolderInfo(folderInfo, hide)
        }
        refreshFolders()
    }

    fun saveFolder(folderInfo: FolderInfo) {
        viewModelScope.launch {
            repository.saveFolderInfo(folderInfo)
        }
        refreshFolders()
    }

    fun deleteFolderInfo(id: Int) {
        viewModelScope.launch {
            repository.deleteFolderInfo(id)
        }
        refreshFolders()
    }

    fun updateFolderWithItems(id: Int, title: String, appInfos: List<AppInfo>) {
        viewModelScope.launch {
            repository.updateFolderWithItems(id, title, appInfos)
        }
    }

    private suspend fun loadFolders() {
        val folders = repository.getAllFolders()
        _folders.value = folders
        _foldersMutable.postValue(folders)
    }
}
