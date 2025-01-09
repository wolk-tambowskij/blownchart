package app.lawnchair.wallpaper.service

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import app.lawnchair.util.MainThreadInitializedObject
import kotlinx.coroutines.runBlocking

@Database(entities = [Wallpaper::class], version = 2, exportSchema = false)
abstract class WallpaperDatabase : RoomDatabase() {

    abstract fun wallpaperDao(): WallpaperDao

    private suspend fun checkpoint() {
        wallpaperDao().checkpoint(SimpleSQLiteQuery("pragma wal_checkpoint(full)"))
    }

    fun checkpointSync() {
        runBlocking {
            checkpoint()
        }
    }

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE wallpapers ADD COLUMN checksum TEXT DEFAULT 'undefined'")
            }
        }
        val INSTANCE = MainThreadInitializedObject { context ->
            Room.databaseBuilder(
                context,
                WallpaperDatabase::class.java,
                "wallpaper_database",
            ).addMigrations(MIGRATION_1_2).build()
        }
    }
}
