/*
 *     Copyright (C) 2026 Wolk Tambowskij
 *
 *     This file is part of the BlownChart fork of Lawnchair Launcher.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package app.blownchart.data.folder.backup

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
