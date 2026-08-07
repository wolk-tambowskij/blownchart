package app.blownchart.data.folder.model

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import app.blownchart.data.folder.backup.FolderImportResult
import app.blownchart.data.folder.service.FolderListEntry
import app.blownchart.data.folder.service.FolderService
import app.blownchart.preferences2.ReloadHelper
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.FolderInfo
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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

    /** Flat folder list (including nested ones) for the Settings management screen. */
    val allFoldersFlat: StateFlow<List<FolderListEntry>> = repository.getAllFoldersFlatFlow()
        .distinctUntilChanged()
        .catch { exception ->
            Log.e("FolderViewModel", "Error in allFoldersFlat flow", exception)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

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
            // appInfo is already the full, resolved new selection, so build the updated
            // FolderInfo from it directly instead of re-reading it back from the DB (which
            // would re-resolve every componentKey through a fresh LauncherApps enumeration).
            _folderInfo.value = FolderInfo().apply {
                this.id = id
                this.title = title
                appInfo.sortedBy { it.title.toString().lowercase(Locale.getDefault()) }
                    .forEach { add(it, false) }
            }
        }
        // Reloading the grid is the expensive part (it triggers a full launcher model rebind);
        // don't pay for it on every single checkbox toggle. onFolderEditingFinished() does it
        // once, when the user actually leaves the folder-editing screen.
    }

    /** Call when leaving the folder-editing screen to apply any pending item changes to the grid. */
    fun onFolderEditingFinished() {
        reloadHelper.reloadGrid()
    }

    /**
     * Persists a manual drag order for the folder list itself; doesn't reload the grid. Reloading
     * immediately on every settle raced with this write (the grid could reload before the new
     * ranks were committed) and caused the dragged folder to visibly snap back. Like
     * [updateFolderItemOrder], the reload is deferred to [onFolderEditingFinished] on screen exit.
     */
    fun updateFolderOrder(orderedFolderIds: List<Int>) {
        viewModelScope.launch {
            repository.updateFolderOrder(orderedFolderIds)
        }
    }

    /** Persists a manual drag order for one folder's own apps; doesn't reload the grid. */
    fun updateFolderItemOrder(folderId: Int, orderedComponentKeys: List<String>) {
        viewModelScope.launch {
            repository.updateFolderItemOrder(folderId, orderedComponentKeys)
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

    /** Folders [folderId] could be nested inside - see [FolderService.getNestableFoldersFlow]. */
    fun nestableFolders(folderId: Int): Flow<List<NestableFolder>> = repository.getNestableFoldersFlow(folderId)
        .map { entities -> entities.map { NestableFolder(it.id, it.title) } }

    /** Nests [folderId] inside [parentId], or moves it back to the top level if null. */
    fun setParentFolder(folderId: Int, parentId: Int?) {
        viewModelScope.launch {
            repository.setParentFolder(folderId, parentId)
        }
        reloadHelper.reloadGrid()
    }

    fun exportFolders(onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            onResult(runCatching { repository.exportFoldersToJson() })
        }
    }

    fun importFolders(json: String, onResult: (Result<FolderImportResult>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching { repository.importFoldersFromJson(json) }
            if (result.isSuccess) reloadHelper.reloadGrid()
            onResult(result)
        }
    }
}

/** UI-friendly stand-in for a [app.blownchart.data.folder.FolderInfoEntity] nesting candidate. */
data class NestableFolder(val id: Int, val title: String)
