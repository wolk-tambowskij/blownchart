package app.blownchart.data.folder.service

import android.content.Context
import android.content.pm.LauncherApps
import android.util.Log
import app.blownchart.data.AppDatabase
import app.blownchart.data.Converters
import app.blownchart.data.folder.FolderInfoEntity
import app.blownchart.data.folder.backup.FolderBackup
import app.blownchart.data.folder.backup.FolderBackupApp
import app.blownchart.data.folder.backup.FolderBackupEntry
import app.blownchart.data.folder.backup.FolderImportResult
import app.blownchart.data.toEntity
import app.blownchart.preferences2.PreferenceManager2
import app.blownchart.util.kotlinxJson
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
import kotlinx.coroutines.flow.flowOn
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
    private var folderPathByComponentKey: Map<String, FolderPath> = emptyMap()

    init {
        folderDao.getComponentKeyToFolderTitleFlow()
            .onEach { tuples ->
                folderPathByComponentKey = tuples.associate {
                    it.componentKey to FolderPath(it.folderTitle, it.parentFolderTitle)
                }
            }
            .launchIn(scope)
    }

    /** Which drawer folder (if any) currently contains [componentKey], for search result labels. */
    fun getFolderPathForComponentKey(componentKey: String): FolderPath? = folderPathByComponentKey[componentKey]

    fun getFoldersFlow(): Flow<List<FolderInfo>> = flow {
        // Cache the componentKey -> AppInfo lookup for the lifetime of this collection instead
        // of rebuilding it (a full LauncherApps.getActivityList() enumeration of every installed
        // app) on every single emission: editing a folder's items writes to the same tables this
        // flow observes, so without the cache, every checkbox toggle re-scanned all apps. Also
        // rebuilt mid-collection, below, if it turns out to be missing something a folder
        // actually references.
        var cachedAppInfoByComponentKey: Map<String, AppInfo>? = null
        // Read once per collection (same lifetime as the cache above) rather than reactively -
        // flipping the order-mode toggle takes effect the next time this screen opens, not live.
        val manualOrder = prefs2.folderManualOrder.firstBlocking()
        // getAllFoldersWithItems() only returns top-level folders; Room's table-level
        // invalidation still re-emits this Flow when a subfolder's own row or items change; since
        // its query touches the same Folders/FolderItems tables (see getSubfoldersWithItems()).
        folderDao.getAllFoldersWithItems().collect { foldersWithItems ->
            if (foldersWithItems.isEmpty()) {
                emit(emptyList())
                return@collect
            }
            // Sort the entities (which carry .rank) before mapping to the domain FolderInfo
            // (which doesn't), so the emitted list order is already correct either way.
            val orderedFoldersWithItems = if (manualOrder) {
                foldersWithItems.sortedBy { it.folder.rank }
            } else {
                foldersWithItems.sortedBy { it.folder.title.lowercase(Locale.getDefault()) }
            }
            val subfoldersByTopFolderId = orderedFoldersWithItems.associate {
                it.folder.id to folderDao.getSubfoldersWithItems(it.folder.id)
            }
            var appInfoByComponentKey = cachedAppInfoByComponentKey
                ?: buildAppInfoByComponentKey().also { cachedAppInfoByComponentKey = it }
            // If an app referenced by a folder item isn't in the cached snapshot yet - e.g.
            // LauncherApps hadn't finished enumerating installed apps this early after a cold
            // start following a backup restore - that app (and, if it was the folder's only
            // content, the whole folder) silently drops out here and stays dropped for the rest
            // of this collection's lifetime, since the cache above is otherwise built once and
            // reused for as long as something keeps observing folders (which can be indefinitely
            // - see the WhileSubscribed(5000) callers). Rebuild once, immediately, instead of
            // waiting on some unrelated future write to these tables to trigger a fresh
            // collection that would happen to rebuild it correctly.
            val allReferencedComponentKeys = (orderedFoldersWithItems.asSequence() + subfoldersByTopFolderId.values.asSequence().flatten())
                .flatMap { it.items.asSequence() }
                .mapNotNull { it.componentKey }
            if (allReferencedComponentKeys.any { it !in appInfoByComponentKey }) {
                appInfoByComponentKey = buildAppInfoByComponentKey().also { cachedAppInfoByComponentKey = it }
            }
            val mapped = orderedFoldersWithItems.mapNotNull { topFolder ->
                mapToFolderInfo(topFolder, true, appInfoByComponentKey, manualOrder, subfoldersByTopFolderId[topFolder.folder.id].orEmpty())
            }
            emit(mapped)
        }
    }.flowOn(Dispatchers.IO)

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
            if (foldersWithItems.isEmpty()) {
                emit(emptyList())
                return@collect
            }
            var appInfoByComponentKey = cachedAppInfoByComponentKey
                ?: buildAppInfoByComponentKey().also { cachedAppInfoByComponentKey = it }
            // Same self-healing rebuild as getFoldersFlow() - see its comment for why.
            val hasUnresolvedItem = foldersWithItems.any { withItems ->
                withItems.items.any { it.componentKey != null && it.componentKey !in appInfoByComponentKey }
            }
            if (hasUnresolvedItem) {
                appInfoByComponentKey = buildAppInfoByComponentKey().also { cachedAppInfoByComponentKey = it }
            }
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
    }.flowOn(Dispatchers.IO)

    suspend fun updateFolderWithItems(folderInfoId: Int, title: String, appInfos: List<AppInfo>) = withContext(Dispatchers.IO) {
        // Ordinary edits (ticking a checkbox in the app picker) replace every item row, since
        // there's no stable id to update in place across saves - preserve whatever manual rank
        // each app already had, and only assign fresh ranks (appended at the end) to apps that
        // are newly added, so a plain selection change never silently resets the manual order.
        val existing = folderDao.getFolderWithItems(folderInfoId)
        val existingRankByComponentKey = existing?.items.orEmpty().associate { it.componentKey to it.rank }
        var nextRank = (existingRankByComponentKey.values.maxOrNull() ?: -1) + 1
        val entities = appInfos.map { appInfo ->
            val key = converters.fromComponentKey(appInfo.toComponentKey())
            val rank = existingRankByComponentKey[key] ?: nextRank++
            appInfo.toEntity(folderInfoId, rank)
        }
        // insertFolder() below is a full-row REPLACE - any field left at its default here would
        // silently reset on every checkbox toggle. Carry over the folder's own rank (drag order
        // among its siblings), hide flag, and parentFolderId (nesting), none of which this method
        // is meant to touch.
        val folderEntity = FolderInfoEntity(
            id = folderInfoId,
            title = title,
            hide = existing?.folder?.hide ?: false,
            rank = existing?.folder?.rank ?: 0,
            parentFolderId = existing?.folder?.parentFolderId,
        )
        folderDao.insertFolderWithItems(folderEntity, entities)
    }

    suspend fun updateFolderOrder(orderedFolderIds: List<Int>) = withContext(Dispatchers.IO) {
        folderDao.updateFolderRanks(orderedFolderIds)
    }

    suspend fun updateFolderItemOrder(folderId: Int, orderedComponentKeys: List<String>) = withContext(Dispatchers.IO) {
        orderedComponentKeys.forEachIndexed { index, componentKey ->
            folderDao.updateFolderItemRank(folderId, componentKey, index)
        }
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
            // Includes this folder's own subfolder (if any), so the app-picker screen can show
            // and let the user drag-reorder it alongside the folder's apps.
            val subfolders = folderDao.getSubfoldersWithItems(folderId)
            mapToFolderInfo(it, hasId, buildAppInfoByComponentKey(), manualOrder, subfolders)
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

            // Recursing with no subfolders of their own keeps this to exactly one level deep -
            // enforced upstream by getNestableFoldersFlow() refusing to offer any nesting targets
            // for a folder that already has subfolders of its own, not by anything here.
            //
            // Subfolders always render as their own block before every app, in both orderings
            // below - manual order only ever reorders folders among themselves and apps among
            // themselves, never interleaves the two blocks.
            val mappedSubfolders = subfolders.mapNotNull { subfolderWithItems ->
                mapToFolderInfo(subfolderWithItems, hasId = true, appInfoByComponentKey, manualOrder)
                    ?.let { subfolderWithItems.folder.rank to it }
            }
            val mappedApps = folderWithItems.items.mapNotNull { itemEntity ->
                itemEntity.componentKey?.let { appInfoByComponentKey[it] }?.let { itemEntity.rank to it }
            }
            if (manualOrder) {
                mappedSubfolders.sortedBy { it.first }.forEach { (_, item) -> domainFolderInfo.add(item, false) }
                mappedApps.sortedBy { it.first }.forEach { (_, item) -> domainFolderInfo.add(item, false) }
            } else {
                // No manual order: everything sorts alphabetically within its own block.
                mappedSubfolders.map { it.second }
                    .sortedBy { it.title.toString().lowercase(Locale.getDefault()) }
                    .forEach { domainFolderInfo.add(it, false) }
                mappedApps.map { it.second }
                    .sortedBy { it.title.toString().lowercase(Locale.getDefault()) }
                    .forEach { domainFolderInfo.add(it, false) }
            }
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
                                    rank = item.rank,
                                )
                            }
                    },
                    parentIndex = parentIndexByFolderId[folderWithItems.folder.id],
                    rank = folderWithItems.folder.rank,
                    hide = folderWithItems.folder.hide,
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
            val resolvedItems = entry.apps.mapNotNull { app ->
                appInfoByPackageAndClass[app.packageName to app.className]?.toEntity(0, app.rank)
            }
            skippedApps += entry.apps.size - resolvedItems.size
            if (resolvedItems.isNotEmpty()) {
                val newId = folderDao.insertNewFolderWithItems(entry.title, resolvedItems)
                folderDao.updateFolderInfo(newId, entry.title, entry.hide)
                folderDao.updateFolderRank(newId, entry.rank)
                newFolderIdByIndex[index] = newId
                importedFolders++
                importedApps += resolvedItems.size
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

/** Which drawer folder contains a search result, and its parent's title if it's nested. */
data class FolderPath(val title: String, val parentTitle: String?)
