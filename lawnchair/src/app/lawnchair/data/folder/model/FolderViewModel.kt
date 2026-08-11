package app.lawnchair.data.folder.model

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import app.lawnchair.data.folder.service.FolderListEntry
import app.lawnchair.data.folder.service.FolderService
import app.lawnchair.preferences2.ReloadHelper
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.FolderInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FolderViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repository: FolderService = FolderService.INSTANCE.get(application)

    val folders: StateFlow<List<FolderInfo>> = repository.getFoldersFlow()
        .distinctUntilChanged()
        .catch { exception ->
            Log.e("FolderViewModel", "Error in folders flow", exception)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    val foldersLiveData: LiveData<List<FolderInfo>> = folders.asLiveData(viewModelScope.coroutineContext)

    /** Flat list (top-level and nested folders alike) for the Settings management screen. */
    val flatFolders: StateFlow<List<FolderListEntry>> = repository.getAllFoldersFlatFlow()
        .distinctUntilChanged()
        .catch { exception ->
            Log.e("FolderViewModel", "Error in flat folders flow", exception)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    private val _nestableFolders = MutableStateFlow<List<FolderInfo>>(emptyList())
    val nestableFolders = _nestableFolders.asStateFlow()

    /** Refreshes [nestableFolders] with the valid nesting targets for [folderId]. */
    fun loadNestableFolders(folderId: Int) {
        viewModelScope.launch {
            _nestableFolders.value = repository.getNestableFolders(folderId)
        }
    }

    /**
     * Nests [folderId] inside [parentFolderId], or un-nests it if [parentFolderId] is null.
     * [onResult] reports whether the change actually applied - see
     * [FolderService.setParentFolder] for why it might not.
     */
    fun setParentFolder(folderId: Int, parentFolderId: Int?, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            onResult(repository.setParentFolder(folderId, parentFolderId))
        }
        reloadHelper.reloadGrid()
    }

    private val _folderInfo = MutableStateFlow<FolderInfo?>(null)
    val folderInfo = _folderInfo.asStateFlow()

    private val reloadHelper = ReloadHelper(application)

    // yeah these should be separate UI actions
    fun setFolderInfo(folderInfoId: Int, hasId: Boolean) {
        viewModelScope.launch {
            _folderInfo.value = repository.getFolderInfo(folderInfoId, hasId)
        }
    }

    fun renameFolder(folderInfo: FolderInfo, hide: Boolean) {
        viewModelScope.launch {
            repository.updateFolderInfo(folderInfo, hide)
        }
        reloadHelper.reloadGrid()
    }

    fun updateFolderItems(id: Int, title: String, appInfo: List<AppInfo>) {
        viewModelScope.launch {
            repository.updateFolderWithItems(id, title, appInfo)
            // Update the local state flow so UI can observe changes without full reload if needed,
            // though for now we just rely on reloadGrid to refresh the launcher.
            // We call reloadGrid *after* the DB update is complete.
            _folderInfo.value = repository.getFolderInfo(id, true)
            reloadHelper.reloadGrid()
        }
    }

    fun createFolder(folderInfo: FolderInfo) {
        viewModelScope.launch {
            repository.saveFolderInfo(folderInfo)
        }
    }

    fun deleteFolder(id: Int) {
        viewModelScope.launch {
            repository.deleteFolderInfo(id)
        }
        reloadHelper.reloadGrid()
    }
}

object FolderOrderUtils {
    private const val DEFAULT_DELIMITER = ","

    fun intListToString(list: List<Int>, delimiter: String = DEFAULT_DELIMITER): String {
        return list.joinToString(delimiter)
    }

    fun stringToIntList(string: String, delimiter: String = DEFAULT_DELIMITER): List<Int> {
        return string.takeIf { it.isNotBlank() }
            ?.split(delimiter)
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?: emptyList()
    }
}
