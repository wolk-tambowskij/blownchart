package app.lawnchair.data.folder.model

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
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

    private val _folderInfo = MutableStateFlow<FolderInfo?>(null)
    val folderInfo = _folderInfo.asStateFlow()

    private val reloadHelper = ReloadHelper(application)
    private var editingFolderChanged = false

    // yeah these should be separate UI actions
    fun setFolderInfo(folderInfoId: Int, hasId: Boolean) {
        editingFolderChanged = false
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

    // Persists every toggle immediately (so nothing is lost to process death), but does not
    // reload the launcher grid here - reloadGrid() is a full model rebind and firing it on
    // every single checkbox toggle in the folder editor made each toggle take multiple
    // seconds. The caller calls onFolderEditingFinished() once, when the user actually
    // leaves the editing screen, instead.
    fun updateFolderItems(id: Int, title: String, appInfo: List<AppInfo>) {
        editingFolderChanged = true
        viewModelScope.launch {
            repository.updateFolderWithItems(id, title, appInfo)
        }
        // The caller already has the fully-resolved new selection on hand - build the
        // updated FolderInfo from that directly instead of re-reading and re-resolving it
        // from the DB via getFolderInfo().
        _folderInfo.value = FolderInfo().apply {
            this.id = id
            this.title = title
            appInfo.sortedBy { it.rank }.forEach { add(it, false) }
        }
    }

    fun onFolderEditingFinished() {
        if (editingFolderChanged) {
            editingFolderChanged = false
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
