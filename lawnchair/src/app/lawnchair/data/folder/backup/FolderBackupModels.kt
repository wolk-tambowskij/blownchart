package app.lawnchair.data.folder.backup

import kotlinx.serialization.Serializable

@Serializable
data class FolderBackup(
    val version: Int = CURRENT_VERSION,
    val folders: List<FolderBackupEntry>,
) {
    companion object {
        const val CURRENT_VERSION = 2
    }
}

@Serializable
data class FolderBackupEntry(
    val title: String,
    val apps: List<FolderBackupApp>,
    // Index into this same FolderBackup.folders list of the folder this one is nested inside,
    // or null for a top-level folder. Absent (defaults to null) in version-1 backups, which
    // predate nesting - those always import as top-level folders, same as before.
    val parentIndex: Int? = null,
    // This folder's own manual-order position among its siblings, and whether it's marked
    // hidden - both meaningless (default) in older backups, same as with parentIndex.
    val rank: Int = 0,
    val hide: Boolean = false,
)

@Serializable
data class FolderBackupApp(
    val packageName: String,
    val className: String,
    // This app's manual-order position within its folder - defaults to 0 (meaning "unordered")
    // for older backups, same as every app would have had before manual order existed.
    val rank: Int = 0,
)

data class FolderImportResult(
    val importedFolders: Int,
    val importedApps: Int,
    val skippedApps: Int,
)
