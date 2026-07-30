package app.lawnchair.data.folder.service

import android.content.Context
import android.content.pm.LauncherApps
import android.util.Log
import app.lawnchair.data.AppDatabase
import app.lawnchair.data.Converters
import app.lawnchair.data.folder.FolderInfoEntity
import app.lawnchair.data.folder.backup.FolderBackup
import app.lawnchair.data.folder.backup.FolderBackupApp
import app.lawnchair.data.folder.backup.FolderBackupEntry
import app.lawnchair.data.folder.backup.FolderImportResult
import app.lawnchair.data.toEntity
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.util.kotlinxJson
import com.android.launcher3.AppFilter
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.pm.UserCache
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.MainThreadInitializedObject
import com.android.launcher3.util.SafeCloseable
import com.patrykmichalik.opto.core.firstBlocking
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

class FolderService(val context: Context) : SafeCloseable {

    private val folderDao = AppDatabase.INSTANCE.get(context).folderDao()
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userCache = UserCache.INSTANCE.get(context)
    private val appFilter = AppFilter(context)
    private val converters = Converters()
    private val scope = MainScope()
    private val prefs2 = PreferenceManager2.getInstance(context)

    // Kept warm for the lifetime of this (app-scoped) singleton, updated only when the folder
    // tables actually change (Room's Flow only emits on writes, not on a timer) - so search, which
    // needs this on every keystroke, never blocks on a DB query.
    @Volatile
    private var folderNameByComponentKey: Map<String, String> = emptyMap()

    init {
        folderDao.getComponentKeyToFolderTitleFlow()
            .onEach { tuples -> folderNameByComponentKey = tuples.associate { it.componentKey to it.folderTitle } }
            .launchIn(scope)
    }

    /** Which drawer folder (if any) currently contains [componentKey], for search result labels. */
    fun getFolderNameForComponentKey(componentKey: String): String? = folderNameByComponentKey[componentKey]

    fun getFoldersFlow(): Flow<List<FolderInfo>> = flow {
        // Cache the componentKey -> AppInfo lookup for the lifetime of this collection instead
        // of rebuilding it (a full LauncherApps.getActivityList() enumeration of every installed
        // app) on every single emission: editing a folder's items writes to the same tables this
        // flow observes, so without the cache, every checkbox toggle re-scanned all apps. The
        // cache resets whenever a fresh collection starts (e.g. the screen is reopened after the
        // StateFlow's WhileSubscribed window drops the subscription), matching how the rest of
        // the app already treats the installed-app list as a per-visit snapshot rather than
        // something tracked live.
        var cachedAppInfoByComponentKey: Map<String, AppInfo>? = null
        // Read once per collection (same lifetime as the cache above) rather than reactively -
        // flipping the order-mode toggle takes effect the next time this screen opens, not live.
        val manualOrder = prefs2.folderManualOrder.firstBlocking()
        // getAllFoldersWithItems() only returns top-level folders; Room's table-level
        // invalidation still re-emits this Flow when a subfolder's own row or items change; since
        // its query touches the same Folders/FolderItems tables (see getSubfoldersWithItems()).
        folderDao.getAllFoldersWithItems().collect { foldersWithItems ->
            val appInfoByComponentKey = cachedAppInfoByComponentKey
                ?: buildAppInfoByComponentKey().also { cachedAppInfoByComponentKey = it }
            // Sort the entities (which carry .rank) before mapping to the domain FolderInfo
            // (which doesn't), so the emitted list order is already correct either way.
            val orderedFoldersWithItems = if (manualOrder) {
                foldersWithItems.sortedBy { it.folder.rank }
            } else {
                foldersWithItems.sortedBy { it.folder.title.lowercase(Locale.getDefault()) }
            }
            val mapped = orderedFoldersWithItems.mapNotNull { topFolder ->
                val subfolders = folderDao.getSubfoldersWithItems(topFolder.folder.id)
                mapToFolderInfo(topFolder, true, appInfoByComponentKey, manualOrder, subfolders)
            }
            emit(mapped)
        }
    }

    /**
     * Every folder (top-level and nested) as a flat list, each paired with its parent's id if
     * any. [getFoldersFlow] embeds a nested folder inside its parent's own contents, which is
     * right for building the drawer but means a nested folder has no entry of its own to manage
     * (rename, delete, un-nest) in a flat list - this is that flat list.
     */
    fun getAllFoldersFlatFlow(): Flow<List<FolderListEntry>> = flow {
        var cachedAppInfoByComponentKey: Map<String, AppInfo>? = null
        val manualOrder = prefs2.folderManualOrder.firstBlocking()
        folderDao.getAllFoldersFlatWithItems().collect { foldersWithItems ->
            val appInfoByComponentKey = cachedAppInfoByComponentKey
                ?: buildAppInfoByComponentKey().also { cachedAppInfoByComponentKey = it }
            // Sort the entities (which carry .rank) before mapping, same as getFoldersFlow(), so
            // the flat management list's order matches the manual-order toggle too.
            val orderedFoldersWithItems = if (manualOrder) {
                foldersWithItems.sortedBy { it.folder.rank }
            } else {
                foldersWithItems.sortedBy { it.folder.title.lowercase(Locale.getDefault()) }
            }
            val mapped = orderedFoldersWithItems.mapNotNull { withItems ->
                mapToFolderInfo(withItems, true, appInfoByComponentKey, manualOrder)
                    ?.let { FolderListEntry(it, withItems.folder.parentFolderId) }
            }
            emit(mapped)
        }
    }

    suspend fun updateFolderWithItems(folderInfoId: Int, title: String, appInfos: List<AppInfo>) = withContext(Dispatchers.IO) {
        // Ordinary edits (ticking a checkbox in the app picker) replace every item row, since
        // there's no stable id to update in place across saves - preserve whatever manual rank
        // each app already had, and only assign fresh ranks (appended at the end) to apps that
        // are newly added, so a plain selection change never silently resets the manual order.
        val existingRankByComponentKey = folderDao.getItemsForFolder(folderInfoId)
            .associate { it.componentKey to it.rank }
        var nextRank = (existingRankByComponentKey.values.maxOrNull() ?: -1) + 1
        val entities = appInfos.map { appInfo ->
            val key = converters.fromComponentKey(appInfo.toComponentKey())
            val rank = existingRankByComponentKey[key] ?: nextRank++
            appInfo.toEntity(folderInfoId, rank)
        }
        folderDao.insertFolderWithItems(FolderInfoEntity(id = folderInfoId, title = title), entities)
    }

    suspend fun updateFolderOrder(orderedFolderIds: List<Int>) = withContext(Dispatchers.IO) {
        folderDao.updateFolderRanks(orderedFolderIds)
    }

    suspend fun updateFolderItemOrder(folderId: Int, orderedComponentKeys: List<String>) = withContext(Dispatchers.IO) {
        folderDao.updateFolderItemRanks(folderId, orderedComponentKeys)
    }

    suspend fun saveFolderInfo(folderInfo: FolderInfo): Unit = withContext(Dispatchers.IO) {
        folderDao.insertFolder(FolderInfoEntity(title = folderInfo.title.toString()))
        Unit
    }

    suspend fun updateFolderInfo(folderInfo: FolderInfo, hide: Boolean = false) = withContext(Dispatchers.IO) {
        folderDao.updateFolderInfo(folderInfo.id, folderInfo.title.toString(), hide)
    }

    suspend fun deleteFolderInfo(id: Int) = withContext(Dispatchers.IO) {
        folderDao.deleteFolder(id)
    }

    /** Folders that could accept [folderId] as a subfolder - see [FolderDao.getNestableFoldersFlow]. */
    fun getNestableFoldersFlow(folderId: Int): Flow<List<FolderInfoEntity>> = folderDao.getNestableFoldersFlow(folderId)

    /**
     * Nests [folderId] one level inside [parentId], or un-nests it back to the top level if
     * [parentId] is null. Only one level of nesting is supported: a folder that already has
     * subfolders of its own can't be nested further (checked by the caller via
     * [getNestableFoldersFlow], which offers no targets at all in that case), and nesting doesn't
     * cascade to any existing children.
     */
    suspend fun setParentFolder(folderId: Int, parentId: Int?) = withContext(Dispatchers.IO) {
        folderDao.setParentFolder(folderId, parentId)
    }

    suspend fun getFolderInfo(folderId: Int, hasId: Boolean = false): FolderInfo? = withContext(Dispatchers.Default) {
        val manualOrder = prefs2.folderManualOrder.firstBlocking()
        folderDao.getFolderWithItems(folderId)?.let {
            mapToFolderInfo(it, hasId, buildAppInfoByComponentKey(), manualOrder)
        }
    }

    private fun mapToFolderInfo(
        folderWithItems: FolderWithItems,
        hasId: Boolean,
        appInfoByComponentKey: Map<String, AppInfo>,
        manualOrder: Boolean,
        subfolders: List<FolderWithItems> = emptyList(),
    ): FolderInfo? {
        return try {
            val domainFolderInfo = FolderInfo().apply {
                // if no id, launcher automatically creates an id for this
                if (hasId) id = folderWithItems.folder.id
                title = folderWithItems.folder.title
            }

            // One level of folder-in-folder nesting: subfolders are added first, so they always
            // sort to the front of the folder rather than being interspersed with apps.
            // Recursing with no subfolders of their own keeps this to exactly one level deep -
            // enforced upstream by getNestableFoldersFlow() refusing to offer any nesting targets
            // for a folder that already has subfolders of its own, not by anything here.
            subfolders.forEach { subfolderWithItems ->
                mapToFolderInfo(subfolderWithItems, hasId = true, appInfoByComponentKey, manualOrder)
                    ?.let { domainFolderInfo.add(it, false) }
            }

            val orderedItems = if (manualOrder) {
                folderWithItems.items.sortedBy { it.rank }
            } else {
                folderWithItems.items
            }
            orderedItems
                .mapNotNull { itemEntity -> itemEntity.componentKey?.let { appInfoByComponentKey[it] } }
                .let { apps ->
                    if (manualOrder) apps else apps.sortedBy { it.title.toString().lowercase(Locale.getDefault()) }
                }
                .forEach { appInfo -> domainFolderInfo.add(appInfo, false) }
            domainFolderInfo
        } catch (e: Exception) {
            Log.e("FolderService", "Failed to map FolderWithItems for id: ${folderWithItems.folder.id}", e)
            null
        }
    }

    private fun buildAppInfoByComponentKey(): Map<String, AppInfo> {
        if (launcherApps == null) return emptyMap()
        return userCache.userProfiles.asSequence()
            .flatMap { launcherApps.getActivityList(null, it) }
            .filter { appFilter.shouldShowApp(it.componentName) }
            .map { AppInfo(context, it, it.user) }
            .mapNotNull { appInfo -> converters.fromComponentKey(appInfo.componentKey)?.let { key -> key to appInfo } }
            .toMap()
    }

    /**
     * Keyed by package + class rather than the full [ComponentKey] (which also encodes the
     * user), since an imported folder scheme is matched against whatever is installed on this
     * device now, not the exact user profile it was exported from.
     */
    private fun buildAppInfoByPackageAndClass(): Map<Pair<String, String>, AppInfo> {
        if (launcherApps == null) return emptyMap()
        return userCache.userProfiles.asSequence()
            .flatMap { launcherApps.getActivityList(null, it) }
            .filter { appFilter.shouldShowApp(it.componentName) }
            .map { AppInfo(context, it, it.user) }
            .mapNotNull { appInfo ->
                appInfo.componentName?.let { component -> (component.packageName to component.className) to appInfo }
            }
            .toMap()
    }

    suspend fun exportFoldersToJson(): String = withContext(Dispatchers.IO) {
        val topLevelFolders = folderDao.getAllFoldersWithItems().first()
        // Each top-level folder is immediately followed by its own subfolders, so a subfolder's
        // parentIndex can reference the top-level entry that was just added before it.
        val orderedFoldersWithItems = mutableListOf<FolderWithItems>()
        val parentIndexByFolderId = mutableMapOf<Int, Int>()
        topLevelFolders.forEach { top ->
            val topIndex = orderedFoldersWithItems.size
            orderedFoldersWithItems.add(top)
            folderDao.getSubfoldersWithItems(top.folder.id).forEach { sub ->
                parentIndexByFolderId[sub.folder.id] = topIndex
                orderedFoldersWithItems.add(sub)
            }
        }
        val backup = FolderBackup(
            folders = orderedFoldersWithItems.map { folderWithItems ->
                FolderBackupEntry(
                    title = folderWithItems.folder.title,
                    apps = folderWithItems.items.mapNotNull { item ->
                        item.componentKey
                            ?.let { ComponentKey.fromString(it) }
                            ?.let { key ->
                                FolderBackupApp(
                                    packageName = key.componentName.packageName,
                                    className = key.componentName.className,
                                )
                            }
                    },
                    parentIndex = parentIndexByFolderId[folderWithItems.folder.id],
                )
            },
        )
        kotlinxJson.encodeToString(backup)
    }

    /**
     * Imports folders as new entries; existing folders are left untouched. Apps not installed
     * on this device are skipped. A subfolder's nesting (backed by [FolderBackupEntry.parentIndex])
     * is restored in a second pass, once every entry's new id is known.
     */
    suspend fun importFoldersFromJson(json: String): FolderImportResult = withContext(Dispatchers.IO) {
        val backup = kotlinxJson.decodeFromString<FolderBackup>(json)
        val appInfoByPackageAndClass = buildAppInfoByPackageAndClass()

        var importedFolders = 0
        var importedApps = 0
        var skippedApps = 0
        val newFolderIdByIndex = mutableMapOf<Int, Int>()

        backup.folders.forEachIndexed { index, entry ->
            val resolvedApps = entry.apps.mapNotNull { app ->
                appInfoByPackageAndClass[app.packageName to app.className]
            }
            skippedApps += entry.apps.size - resolvedApps.size
            if (resolvedApps.isNotEmpty()) {
                newFolderIdByIndex[index] = folderDao.insertNewFolderWithItems(entry.title, resolvedApps.map { it.toEntity(0) })
                importedFolders++
                importedApps += resolvedApps.size
            }
        }
        backup.folders.forEachIndexed { index, entry ->
            val newId = newFolderIdByIndex[index] ?: return@forEachIndexed
            val newParentId = entry.parentIndex?.let { newFolderIdByIndex[it] } ?: return@forEachIndexed
            folderDao.setParentFolder(newId, newParentId)
        }

        FolderImportResult(importedFolders, importedApps, skippedApps)
    }

    override fun close() {
        TODO("Not yet implemented")
    }

    companion object {
        @JvmField
        val INSTANCE = MainThreadInitializedObject(::FolderService)
    }
}

/** A folder from the flat management list, paired with its parent's id if it's nested. */
data class FolderListEntry(val folderInfo: FolderInfo, val parentFolderId: Int?)
