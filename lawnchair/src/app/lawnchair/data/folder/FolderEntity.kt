package app.lawnchair.data.folder

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "Folders",
    indices = [Index(value = ["parentFolderId"])],
)
data class FolderInfoEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val hide: Boolean = false,
    val rank: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    // Null for a top-level folder. Set to another folder's id to nest this folder inside it -
    // only one level deep is supported, i.e. a folder with a non-null parentFolderId can't
    // itself be a parent. No Room-level foreign key/cascade here (kept consistent with the
    // rest of this table); deleting a parent explicitly deletes its children in FolderDao.
    val parentFolderId: Int? = null,
)

@Entity(
    tableName = "FolderItems",
    foreignKeys = [
        ForeignKey(
            entity = FolderInfoEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["folderId"])],
)
data class FolderItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val folderId: Int,
    val rank: Int = 0,
    @ColumnInfo(name = "item_info") val componentKey: String?,
    val timestamp: Long = System.currentTimeMillis(),
)
