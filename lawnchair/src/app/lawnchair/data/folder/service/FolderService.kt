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
import app.lawnchair.util.kotlinxJson
import com.android.launcher3.AppFilter
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.pm.UserCache
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.MainThreadInitializedObject
import com.android.launcher3.util.SafeCloseable
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

class FolderService(val context: Context) : SafeCloseable {

    private val folderDao = AppDatabase.INSTANCE.get(context).folderDao()
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userCache = UserCache.INSTANCE.get(context)
    private val appFilter = AppFilter(context)
    private val converters = Converters()

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
        folderDao.getAllFoldersWithItems().collect { foldersWithItems ->
            val appInfoByComponentKey = cachedAppInfoByComponentKey
                ?: buildAppInfoByComponentKey().also { cachedAppInfoByComponentKey = it }
            emit(foldersWithItems.mapNotNull { mapToFolderInfo(it, true, appInfoByComponentKey) })
        }
    }

    suspend fun updateFolderWithItems(folderInfoId: Int, title: String, appInfos: List<AppInfo>) = withContext(Dispatchers.IO) {
        folderDao.insertFolderWithItems(
            FolderInfoEntity(id = folderInfoId, title = title),
            appInfos.map { it.toEntity(folderInfoId) },
        )
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

    suspend fun getFolderInfo(folderId: Int, hasId: Boolean = false): FolderInfo? = withContext(Dispatchers.Default) {
        folderDao.getFolderWithItems(folderId)?.let {
            mapToFolderInfo(it, hasId, buildAppInfoByComponentKey())
        }
    }

    private fun mapToFolderInfo(
        folderWithItems: FolderWithItems,
        hasId: Boolean,
        appInfoByComponentKey: Map<String, AppInfo>,
    ): FolderInfo? {
        return try {
            val domainFolderInfo = FolderInfo().apply {
                // if no id, launcher automatically creates an id for this
                if (hasId) id = folderWithItems.folder.id
                title = folderWithItems.folder.title
            }

            // Folder contents are sorted alphabetically on read; no manual order is persisted.
            folderWithItems.items
                .mapNotNull { itemEntity -> itemEntity.componentKey?.let { appInfoByComponentKey[it] } }
                .sortedBy { it.title.toString().lowercase(Locale.getDefault()) }
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
        val foldersWithItems = folderDao.getAllFoldersWithItems().first()
        val backup = FolderBackup(
            folders = foldersWithItems.map { folderWithItems ->
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
                )
            },
        )
        kotlinxJson.encodeToString(backup)
    }

    /**
     * Imports folders as new entries; existing folders are left untouched. Apps not installed
     * on this device are skipped.
     */
    suspend fun importFoldersFromJson(json: String): FolderImportResult = withContext(Dispatchers.IO) {
        val backup = kotlinxJson.decodeFromString<FolderBackup>(json)
        val appInfoByPackageAndClass = buildAppInfoByPackageAndClass()

        var importedFolders = 0
        var importedApps = 0
        var skippedApps = 0

        backup.folders.forEach { entry ->
            val resolvedApps = entry.apps.mapNotNull { app ->
                appInfoByPackageAndClass[app.packageName to app.className]
            }
            skippedApps += entry.apps.size - resolvedApps.size
            if (resolvedApps.isNotEmpty()) {
                folderDao.insertNewFolderWithItems(entry.title, resolvedApps.map { it.toEntity(0) })
                importedFolders++
                importedApps += resolvedApps.size
            }
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
