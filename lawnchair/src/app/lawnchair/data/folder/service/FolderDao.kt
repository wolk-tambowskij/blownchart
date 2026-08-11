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

    @Query("SELECT * FROM FolderItems WHERE folderId IS NOT :folderId")
    @Transaction
    suspend fun getItems(folderId: Int): List<FolderItemEntity>

    @Query("SELECT * FROM Folders")
    fun getAllFolders(): Flow<List<FolderInfoEntity>>

    /** Top-level folders only - used to build the actual app-drawer folder list. */
    @Query("SELECT * FROM Folders WHERE parentFolderId IS NULL")
    fun getTopLevelFolders(): Flow<List<FolderInfoEntity>>

    /** Other top-level folders [folderId] could be nested into (excludes itself). */
    @Query("SELECT * FROM Folders WHERE parentFolderId IS NULL AND id != :folderId")
    suspend fun getNestableFolders(folderId: Int): List<FolderInfoEntity>

    @Transaction
    suspend fun insertFolderWithItems(folder: FolderInfoEntity, items: List<FolderItemEntity>) {
        insertFolder(folder)
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

    @Query("SELECT * FROM Folders WHERE parentFolderId = :parentFolderId")
    suspend fun getChildFolders(parentFolderId: Int): List<FolderInfoEntity>

    @Query("UPDATE Folders SET parentFolderId = :parentFolderId WHERE id = :folderId")
    suspend fun setParentFolder(folderId: Int, parentFolderId: Int?)

    /**
     * Deletes [folderId] and, since nesting has no Room-level cascade (see
     * [FolderInfoEntity.parentFolderId]), explicitly deletes any child folders (and their
     * items) first.
     */
    @Transaction
    suspend fun deleteFolderWithChildren(folderId: Int) {
        getChildFolders(folderId).forEach { child ->
            deleteFolderItemsByFolderId(child.id)
            deleteFolder(child.id)
        }
        deleteFolderItemsByFolderId(folderId)
        deleteFolder(folderId)
    }

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
