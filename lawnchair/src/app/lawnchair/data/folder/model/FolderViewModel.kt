package app.lawnchair.data.folder.model

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import app.lawnchair.data.folder.backup.FolderImportResult
import app.lawnchair.data.folder.service.FolderService
import app.lawnchair.preferences2.ReloadHelper
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

    /** Folders [folderId] could be nested inside (excludes itself and anything already a parent). */
    fun nestableFolders(folderId: Int): Flow<List<NestableFolder>> =
        repository.getNestableFoldersFlow(excludingFolderId = folderId)
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

/** UI-friendly stand-in for a [app.lawnchair.data.folder.FolderInfoEntity] nesting candidate. */
data class NestableFolder(val id: Int, val title: String)
