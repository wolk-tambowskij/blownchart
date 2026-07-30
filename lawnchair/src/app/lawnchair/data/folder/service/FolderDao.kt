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
    suspend fun insertFolder(folder: FolderInfoEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolderItems(items: List<FolderItemEntity>)

    @Query("SELECT * FROM Folders WHERE id = :folderId")
    @Transaction
    suspend fun getFolderWithItems(folderId: Int): FolderWithItems?

    @Query("SELECT * FROM FolderItems WHERE folderId IS NOT :folderId")
    @Transaction
    suspend fun getItems(folderId: Int): List<FolderItemEntity>

    @Query("SELECT * FROM FolderItems WHERE folderId = :folderId")
    suspend fun getItemsForFolder(folderId: Int): List<FolderItemEntity>

    @Query("SELECT * FROM Folders")
    @Transaction
    fun getAllFoldersWithItems(): Flow<List<FolderWithItems>>

    /**
     * Lightweight component-key -> folder-title lookup for search result labels - avoids
     * pulling the full [FolderWithItems] (and, downstream, an [AppInfo] resolve per item) just to
     * answer "which folder is this app in". Folders marked [FolderInfoEntity.hide] are excluded:
     * if a folder is meant to stay out of sight, its membership shouldn't leak via search either.
     */
    @Query(
        """
        SELECT fi.item_info AS componentKey, f.title AS folderTitle
        FROM FolderItems fi
        JOIN Folders f ON fi.folderId = f.id
        WHERE fi.item_info IS NOT NULL AND f.hide = 0
        """,
    )
    fun getComponentKeyToFolderTitleFlow(): Flow<List<ComponentKeyToFolderTitle>>

    @Transaction
    suspend fun insertFolderWithItems(folder: FolderInfoEntity, items: List<FolderItemEntity>) {
        insertFolder(folder)
        // FolderItemEntity.id is auto-generated, so a plain insert would never replace an
        // item that was removed from the folder (it would just accumulate stale rows and let
        // the removed app resurface on the next read). Clear the old set first.
        deleteFolderItemsByFolderId(folder.id)
        insertFolderItems(items)
    }

    @Query("DELETE FROM FolderItems WHERE folderId = :folderId")
    suspend fun deleteFolderItemsByFolderId(folderId: Int)

    /** Inserts a brand-new folder (auto-generated id) with its items, e.g. for import. */
    @Transaction
    suspend fun insertNewFolderWithItems(title: String, items: List<FolderItemEntity>): Int {
        val folderId = insertFolder(FolderInfoEntity(title = title)).toInt()
        insertFolderItems(items.map { it.copy(folderId = folderId) })
        return folderId
    }

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

    @Query("UPDATE Folders SET rank = :rank WHERE id = :folderId")
    suspend fun updateFolderRank(folderId: Int, rank: Int)

    /** Persists a manual drag order for the folder list itself. */
    @Transaction
    suspend fun updateFolderRanks(orderedFolderIds: List<Int>) {
        orderedFolderIds.forEachIndexed { index, id -> updateFolderRank(id, index) }
    }

    @Query("UPDATE FolderItems SET rank = :rank WHERE folderId = :folderId AND item_info = :componentKey")
    suspend fun updateFolderItemRank(folderId: Int, componentKey: String, rank: Int)

    /** Persists a manual drag order for the apps within one folder. */
    @Transaction
    suspend fun updateFolderItemRanks(folderId: Int, orderedComponentKeys: List<String>) {
        orderedComponentKeys.forEachIndexed { index, key -> updateFolderItemRank(folderId, key, index) }
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

data class ComponentKeyToFolderTitle(
    val componentKey: String,
    val folderTitle: String,
)
