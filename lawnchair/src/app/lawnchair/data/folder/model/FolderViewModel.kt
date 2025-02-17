package app.lawnchair.data.folder.model

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lawnchair.data.folder.service.FolderService
import app.lawnchair.preferences2.ReloadHelper
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.FolderInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FolderViewModel(
    context: Context,
    private val repository: FolderService = FolderService.INSTANCE.get(context),
) : ViewModel() {
    private val _folders = MutableStateFlow<List<FolderInfo>>(emptyList())
    val folders: StateFlow<List<FolderInfo>> = _folders.asStateFlow()

    private val _foldersMutable = MutableLiveData<List<FolderInfo>>()
    val foldersMutable: LiveData<List<FolderInfo>> = _foldersMutable

    private val _folderInfo = MutableStateFlow<FolderInfo?>(null)
    val folderInfo = _folderInfo.asStateFlow()

    private val mutex = Mutex()
    private val reloadHelper = ReloadHelper(context)

    init {
        refreshFolders()
        viewModelScope.launch {
        }
    }

    fun refreshFolders() {
        viewModelScope.launch {
            mutex.withLock {
                loadFolders()
            }
            reloadHelper.reloadGrid()
        }
    }

    fun setFolderInfo(folderInfoId: Int, hasId: Boolean) {
        viewModelScope.launch {
            _folderInfo.value = repository.getFolderInfo(folderInfoId, hasId)
        }
        refreshFolders()
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

    fun updateFolder(id: Int, title: String, appInfo: List<AppInfo>) {
        viewModelScope.launch {
            repository.updateFolderWithItems(id, title, appInfo)
        }
        refreshFolders()
    }

    fun deleteFolder(id: Int) {
        viewModelScope.launch {
            repository.deleteFolderInfo(id)
        }
        refreshFolders()
    }

    private suspend fun loadFolders() {
        val folders = repository.getAllFolders()
        _folders.update { folders }
        _foldersMutable.postValue(folders)
    }
}
