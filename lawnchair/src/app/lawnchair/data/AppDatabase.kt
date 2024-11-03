package app.lawnchair.data

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SimpleSQLiteQuery
import app.lawnchair.data.iconoverride.IconOverride
import app.lawnchair.data.iconoverride.IconOverrideDao
import com.android.launcher3.util.MainThreadInitializedObject
import com.android.launcher3.util.SafeCloseable

@Database(entities = [IconOverride::class], version = 1)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase(), SafeCloseable {

    abstract fun iconOverrideDao(): IconOverrideDao

    suspend fun checkpoint() {
        iconOverrideDao().checkpoint(SimpleSQLiteQuery("pragma wal_checkpoint(full)"))
    }

    companion object {
        val INSTANCE = MainThreadInitializedObject { context ->
            Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "preferences"
            ).build()
        }
    }
}
