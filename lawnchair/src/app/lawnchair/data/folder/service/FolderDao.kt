package app.lawnchair.data.folder.service

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Relation
import androidx.room.Transaction
import androidx.sqlite.db.SupportSQLiteQuery
import app.lawnchair.data.folder.FolderInfoEntity
import app.lawnchair.data.folder.FolderItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: FolderInfoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolderItems(items: List<FolderItemEntity>)

    @Query("SELECT * FROM Folders WHERE id = :folderId")
    @Transaction
    suspend fun getFolderWithItems(folderId: Int): FolderWithItems?

    // Single batched query instead of one getFolderWithItems() call per folder - avoids an
    // N+1 query pattern when loading the full folder list.
    @Query("SELECT * FROM Folders")
    @Transaction
    fun getAllFoldersWithItems(): Flow<List<FolderWithItems>>

    @Query("SELECT * FROM FolderItems WHERE folderId IS NOT :folderId")
    @Transaction
    suspend fun getItems(folderId: Int): List<FolderItemEntity>

    @Query("SELECT * FROM Folders")
    fun getAllFolders(): Flow<List<FolderInfoEntity>>

    @Transaction
    suspend fun insertFolderWithItems(folder: FolderInfoEntity, items: List<FolderItemEntity>) {
        insertFolder(folder)
        // FolderItemEntity uses an auto-generated id, so OnConflictStrategy.REPLACE on
        // insertFolderItems() never matches an existing row for this folder - without this
        // delete, unchecking an app in the folder editor would never remove its row, and
        // rows for removed apps would accumulate indefinitely (and could resurface on a
        // later read, since resolution only looks up componentKey -> AppInfo, not whether
        // the row is still wanted).
        deleteFolderItemsByFolderId(folder.id)
        insertFolderItems(items)
    }

    @Query("DELETE FROM FolderItems WHERE folderId = :folderId")
    suspend fun deleteFolderItemsByFolderId(folderId: Int)

    @Query(
        value = """
                UPDATE Folders
                SET title = :newTitle, hide = :hide, timestamp = :timestamp
                WHERE id = :folderId
            """,
    )
    suspend fun updateFolderInfo(
        folderId: Int,
        newTitle: String,
        hide: Boolean,
        timestamp: Long = System.currentTimeMillis(),
    )

    @Query("DELETE FROM Folders WHERE id = :folderId")
    suspend fun deleteFolder(folderId: Int)

    @RawQuery
    suspend fun checkpoint(supportSQLiteQuery: SupportSQLiteQuery): Int
}

data class FolderWithItems(
    @Embedded val folder: FolderInfoEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "folderId",
    )
    val items: List<FolderItemEntity>,
)
