package app.lawnchair.data.folder.service

import android.content.Context
import android.content.pm.LauncherApps
import android.util.Log
import app.lawnchair.data.AppDatabase
import app.lawnchair.data.Converters
import app.lawnchair.data.folder.FolderInfoEntity
import app.lawnchair.data.toEntity
import com.android.launcher3.AppFilter
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.pm.UserCache
import com.android.launcher3.util.MainThreadInitializedObject
import com.android.launcher3.util.SafeCloseable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class FolderService(val context: Context) : SafeCloseable {

    private val folderDao = AppDatabase.INSTANCE.get(context).folderDao()
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userCache = UserCache.INSTANCE.get(context)
    private val appFilter = AppFilter(context)
    private val converters = Converters()

    fun getFoldersFlow(): Flow<List<FolderInfo>> {
        return folderDao.getAllFoldersWithItems().map { foldersWithItems ->
            // Resolved once per emission and shared across every folder/item below, instead
            // of toItemInfo() re-enumerating every installed app once per folder item.
            val appInfoByComponentKey = buildAppInfoMap()
            foldersWithItems.mapNotNull { folderWithItems ->
                mapToFolderInfo(folderWithItems, hasId = true, appInfoByComponentKey)
            }
        }
    }

    suspend fun updateFolderWithItems(folderInfoId: Int, title: String, appInfos: List<AppInfo>) = withContext(Dispatchers.IO) {
        folderDao.insertFolderWithItems(
            FolderInfoEntity(id = folderInfoId, title = title),
            appInfos.mapIndexed { index, appInfo ->
                appInfo.toEntity(folderInfoId).copy(rank = index)
            }.toList(),
        )
    }

    suspend fun saveFolderInfo(folderInfo: FolderInfo) = withContext(Dispatchers.IO) {
        folderDao.insertFolder(FolderInfoEntity(title = folderInfo.title.toString()))
    }

    suspend fun updateFolderInfo(folderInfo: FolderInfo, hide: Boolean = false) = withContext(Dispatchers.IO) {
        folderDao.updateFolderInfo(folderInfo.id, folderInfo.title.toString(), hide)
    }

    suspend fun deleteFolderInfo(id: Int) = withContext(Dispatchers.IO) {
        folderDao.deleteFolder(id)
    }

    suspend fun getFolderInfo(folderId: Int, hasId: Boolean = false): FolderInfo? = withContext(Dispatchers.Default) {
        folderDao.getFolderWithItems(folderId)?.let {
            mapToFolderInfo(it, hasId, buildAppInfoMap())
        }
    }

    private fun mapToFolderInfo(folderWithItems: FolderWithItems, hasId: Boolean, appInfoByComponentKey: Map<String, AppInfo>): FolderInfo? {
        return try {
            val domainFolderInfo = FolderInfo().apply {
                // if no id, launcher automatically creates an id for this
                if (hasId) id = folderWithItems.folder.id
                title = folderWithItems.folder.title
            }

            folderWithItems.items.sortedBy { it.rank }.forEach { itemEntity ->
                appInfoByComponentKey[itemEntity.componentKey]?.let { appInfo ->
                    domainFolderInfo.add(appInfo, false)
                }
            }
            domainFolderInfo
        } catch (e: Exception) {
            Log.e("FolderService", "Failed to map FolderWithItems for id: ${folderWithItems.folder.id}", e)
            null
        }
    }

    // Builds the componentKey -> AppInfo lookup once instead of once per folder item;
    // callers resolving multiple folders/items should build this once and share it.
    private fun buildAppInfoMap(): Map<String, AppInfo> {
        if (launcherApps == null) return emptyMap()
        return userCache.userProfiles.asSequence()
            .flatMap { launcherApps.getActivityList(null, it) }
            .filter { appFilter.shouldShowApp(it.componentName) }
            .map { AppInfo(context, it, it.user) }
            // componentName is null for e.g. the Private Space install-button entry.
            .filter { it.componentName != null }
            .associateBy { converters.fromComponentKey(it.componentKey) }
    }

    suspend fun getAllFolders(): List<FolderInfo> = withContext(Dispatchers.Main) {
        try {
            val foldersWithItems = folderDao.getAllFoldersWithItems().firstOrNull() ?: emptyList()
            val appInfoByComponentKey = buildAppInfoMap()
            foldersWithItems.mapNotNull { mapToFolderInfo(it, hasId = true, appInfoByComponentKey) }
        } catch (e: Exception) {
            Log.e("FolderService", "Failed to get all folders", e)
            emptyList()
        }
    }

    override fun close() {
        TODO("Not yet implemented")
    }

    companion object {
        @JvmField
        val INSTANCE = MainThreadInitializedObject(::FolderService)
    }
}
