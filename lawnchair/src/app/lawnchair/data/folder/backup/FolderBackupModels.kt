package app.lawnchair.data.folder.backup

import kotlinx.serialization.Serializable

@Serializable
data class FolderBackup(
    val version: Int = CURRENT_VERSION,
    val folders: List<FolderBackupEntry>,
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

@Serializable
data class FolderBackupEntry(
    val title: String,
    val apps: List<FolderBackupApp>,
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
