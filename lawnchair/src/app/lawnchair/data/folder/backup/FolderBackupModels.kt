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
)

@Serializable
data class FolderBackupApp(
    val packageName: String,
    val className: String,
)

data class FolderImportResult(
    val importedFolders: Int,
    val importedApps: Int,
    val skippedApps: Int,
)
