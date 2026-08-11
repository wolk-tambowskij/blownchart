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
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.MainThreadInitializedObject
import com.android.launcher3.util.SafeCloseable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext

class FolderService(val context: Context) : SafeCloseable {

    private val folderDao = AppDatabase.INSTANCE.get(context).folderDao()
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userCache = UserCache.INSTANCE.get(context)
    private val appFilter = AppFilter(context)
    private val converters = Converters()
    private val scope = MainScope()

    // Kept warm for the lifetime of this (app-scoped) singleton, updated only when the folder
    // tables actually change (Room's Flow only emits on writes, not on a timer) - so search, which
    // needs this on every keystroke, never blocks on a DB query.
    @Volatile
    private var folderPathByComponentKey: Map<String, FolderPath> = emptyMap()

    init {
        getFoldersFlow()
            .onEach { folders ->
                val map = mutableMapOf<String, FolderPath>()
                folders.forEach { folder ->
                    folder.getContents().forEach { item ->
                        if (item is FolderInfo) {
                            // A nested subfolder is embedded as its own item inside its parent's
                            // contents (see getFoldersFlow) - recurse one level to map its own
                            // apps, tagged with the top-level folder as parent context for the
                            // "In Parent → Folder" subtitle.
                            item.getContents().forEach { subItem ->
                                subItem.targetComponent?.let {
                                    map[ComponentKey(it, subItem.user).toString()] =
                                        FolderPath(item.title.toString(), folder.title.toString())
                                }
                            }
                        } else {
                            item.targetComponent?.let {
                                map[ComponentKey(it, item.user).toString()] = FolderPath(folder.title.toString(), null)
                            }
                        }
                    }
                }
                folderPathByComponentKey = map
            }
            .launchIn(scope)
    }

    /** Which drawer folder (if any) currently contains [componentKey], for search result labels. */
    fun getFolderPathForComponentKey(componentKey: String): FolderPath? = folderPathByComponentKey[componentKey]

    /**
     * Top-level folders only, for building the actual app-drawer folder list. A nested folder
     * is embedded as its own [FolderInfo] item inside its parent's contents (appended after the
     * plain apps) rather than listed as a second top-level entry - [FolderInfo] extends
     * [com.android.launcher3.model.data.CollectionInfo], itself an [com.android.launcher3.model.data.ItemInfo],
     * so this is a first-class content item, the same way an app pair already is. Recursing with
     * no further children keeps this to exactly one level deep, enforced by
     * [getNestableFolders] only offering childless folders as valid nesting targets elsewhere,
     * not by anything here.
     */
    fun getFoldersFlow(): Flow<List<FolderInfo>> {
        return folderDao.getTopLevelFolders().map { topLevelEntities ->
            topLevelEntities.mapNotNull { topEntity ->
                val topFolder = getFolderInfo(topEntity.id, true) ?: return@mapNotNull null
                folderDao.getChildFolders(topEntity.id).forEach { childEntity ->
                    getFolderInfo(childEntity.id, true)?.let { topFolder.add(it, false) }
                }
                topFolder
            }
        }
    }

    /**
     * Every folder (top-level and nested) as a flat list, each paired with its parent's id if
     * any. [getFoldersFlow] only returns top-level folders (right for building the drawer), but
     * a nested folder still needs its own entry to manage (rename, delete, un-nest) from
     * Settings - this is that flat list.
     */
    fun getAllFoldersFlatFlow(): Flow<List<FolderListEntry>> {
        return folderDao.getAllFolders().map { folderEntities ->
            folderEntities.mapNotNull { folderEntity ->
                getFolderInfo(folderEntity.id, true)?.let { FolderListEntry(it, folderEntity.parentFolderId) }
            }
        }
    }

    /**
     * Valid nesting targets for [folderId]: other top-level folders. Empty if [folderId] itself
     * already has children, since nesting it further would push those children two levels deep,
     * which isn't supported.
     */
    suspend fun getNestableFolders(folderId: Int): List<FolderInfo> = withContext(Dispatchers.IO) {
        if (folderDao.getChildFolders(folderId).isNotEmpty()) return@withContext emptyList()
        folderDao.getNestableFolders(folderId).mapNotNull { getFolderInfo(it.id, true) }
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
        folderDao.deleteFolderWithChildren(id)
    }

    /**
     * Nests [folderId] inside [parentFolderId], or un-nests it back to the top level if
     * [parentFolderId] is null. Only one level of nesting is supported, so this is a no-op
     * (returns false) if [parentFolderId] itself already has a parent, or if [folderId]
     * already has children of its own.
     */
    suspend fun setParentFolder(folderId: Int, parentFolderId: Int?): Boolean = withContext(Dispatchers.IO) {
        if (parentFolderId != null) {
            val parent = folderDao.getFolderWithItems(parentFolderId)?.folder
            if (parent?.parentFolderId != null) return@withContext false
            if (folderDao.getChildFolders(folderId).isNotEmpty()) return@withContext false
        }
        folderDao.setParentFolder(folderId, parentFolderId)
        true
    }

    suspend fun getFolderInfo(folderId: Int, hasId: Boolean = false): FolderInfo? = withContext(Dispatchers.Default) {
        folderDao.getFolderWithItems(folderId)?.let {
            mapToFolderInfo(it, hasId)
        }
    }

    private fun mapToFolderInfo(folderWithItems: FolderWithItems, hasId: Boolean): FolderInfo? {
        return try {
            val domainFolderInfo = FolderInfo().apply {
                // if no id, launcher automatically creates an id for this
                if (hasId) id = folderWithItems.folder.id
                title = folderWithItems.folder.title
            }

            folderWithItems.items.sortedBy { it.rank }.forEach { itemEntity ->
                // Consider caching toItemInfo results if componentKey lookups are slow
                // and items don't change frequently without folder data changing
                toItemInfo(itemEntity.componentKey)?.let { appInfo ->
                    domainFolderInfo.add(appInfo, false)
                }
            }
            domainFolderInfo
        } catch (e: Exception) {
            Log.e("FolderService", "Failed to map FolderWithItems for id: ${folderWithItems.folder.id}", e)
            null
        }
    }

    private fun toItemInfo(componentKey: String?): AppInfo? {
        if (launcherApps != null) {
            return userCache.userProfiles.asSequence()
                .flatMap { launcherApps.getActivityList(null, it) }
                .filter { appFilter.shouldShowApp(it.componentName) }
                .map { AppInfo(context, it, it.user) }
                .filter { converters.fromComponentKey(it.componentKey) == componentKey }
                .firstOrNull()
        }
        return null
    }

    suspend fun getAllFolders(): List<FolderInfo> = withContext(Dispatchers.Main) {
        try {
            val folderEntities = folderDao.getAllFolders().firstOrNull() ?: emptyList()
            folderEntities.mapNotNull { folderEntity ->
                getFolderInfo(folderEntity.id, true)
            }
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

data class FolderListEntry(val folderInfo: FolderInfo, val parentFolderId: Int?)

/** Folder title (and, for a nested subfolder, its top-level parent's title) for search labels. */
data class FolderPath(val title: String, val parentTitle: String?)
