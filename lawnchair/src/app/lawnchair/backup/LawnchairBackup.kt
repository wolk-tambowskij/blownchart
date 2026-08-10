package app.lawnchair.backup

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.graphics.drawable.toBitmap
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import app.lawnchair.LawnchairProto.BackupInfo
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.util.hasFlag
import app.lawnchair.util.scaleDownTo
import app.lawnchair.util.scaleDownToDisplaySize
import app.lawnchair.wallpaper.WallpaperColorsCompat
import app.lawnchair.wallpaper.WallpaperManagerCompat
import com.android.launcher3.BuildConfig
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherFiles
import com.android.launcher3.R
import com.android.launcher3.model.DeviceGridState
import com.android.launcher3.provider.RestoreDbTask
import com.google.protobuf.Timestamp
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class LawnchairBackup(
    private val context: Context,
    private val uri: Uri,
) {
    lateinit var info: BackupInfo
    var screenshot: Bitmap? = null
    var wallpaper: Bitmap? = null

    suspend fun readInfoAndPreview() {
        var tmpScreenshot: Bitmap? = null
        var tmpWallpaper: Bitmap? = null
        readZip(
            mapOf(
                INFO_FILE_NAME to { info = BackupInfo.newBuilder().mergeFrom(it).build() },
                SCREENSHOT_FILE_NAME to { tmpScreenshot = BitmapFactory.decodeStream(it) },
                WALLPAPER_FILE_NAME to { tmpWallpaper = BitmapFactory.decodeStream(it) },
            ),
        )
        val size = max(info.previewWidth, info.previewHeight).coerceAtMost(4000)
        screenshot = tmpScreenshot?.scaleDownTo(size)
        wallpaper = tmpWallpaper?.scaleDownToDisplaySize(context)
    }

    suspend fun restore(selectedContents: Int) {
        val handlers = mutableMapOf<String, suspend (InputStream) -> Unit>()
        val contents = selectedContents and info.contents
        if (contents.hasFlag(INCLUDE_LAYOUT_AND_SETTINGS)) {
            getFiles(context, forRestore = true).forEach { (name, file) ->
                handlers[name] = when (name) {
                    // SharedPreferences/DataStore both cache a file's contents in memory once
                    // anything in this process has read them - virtually guaranteed by the time
                    // the user reaches this screen - and both blindly flush that (by then stale)
                    // full in-memory copy back to disk on their next write from anywhere,
                    // silently clobbering a raw byte-level restore of the same file before the
                    // process even gets to restart. restoreSharedPreferencesFile/
                    // restoreDataStoreFile replay the backup's entries through the same live,
                    // cached instance every other reader in this process shares instead, so
                    // nothing is left to disagree with. This is what dropped the custom
                    // home/drawer grid size (stored via classic SharedPreferences) back to
                    // default on the first restore pass.
                    PREFS_FILE_NAME -> { input -> restoreSharedPreferencesFile(file, input) }

                    PREFS_DATASTORE_FILE_NAME -> { input -> restoreDataStoreFile(file, input) }

                    else -> {
                        { input ->
                            file.parentFile?.mkdirs()
                            input.copyTo(file.outputStream())
                        }
                    }
                }
            }
        }
        if (contents.hasFlag(INCLUDE_WALLPAPER)) {
            handlers[WALLPAPER_FILE_NAME] = {
                val wallpaperManager = WallpaperManager.getInstance(context)
                wallpaperManager.setBitmap(BitmapFactory.decodeStream(it))
            }
        }
        // Only clears out a stale restored.db (and its -wal/-shm/-journal sidecars) left over
        // from a previous restore attempt - NOT the whole databases/ directory. That directory
        // is also home to unrelated SQLite databases this process has open independently (e.g.
        // the icon cache), and deleting it out from under them raced with a background write on
        // one of those and crashed the whole process with SQLITE_READONLY_DBMOVED.
        val restoredDbFile = context.getDatabasePath(RESTORED_DB_FILE_NAME)
        restoredDbFile.parentFile?.listFiles()
            ?.filter { it.name.startsWith(RESTORED_DB_FILE_NAME) }
            ?.forEach { it.delete() }
        // Same reasoning, for the "preferences" Room db (icon overrides, wallpaper metadata, and
        // anything else layered on top of it) - it's WAL-mode, so a raw copy of just the main db
        // file leaves this process's pre-restore -wal/-shm sidecars sitting next to the newly-
        // restored file. On the next open, SQLite replays that stale WAL on top of it, silently
        // reverting whatever restore just wrote. Deleting the sidecars (not the main file - that's
        // about to be overwritten by the zip entry itself) forces a clean read of only what
        // restore actually wrote.
        if (contents.hasFlag(INCLUDE_LAYOUT_AND_SETTINGS)) {
            val prefsDb = prefsDbFile(context)
            prefsDb.parentFile?.listFiles()
                ?.filter { it.name.startsWith(prefsDb.name) && it.name != prefsDb.name }
                ?.forEach { it.delete() }
        }
        readZip(handlers)

        // Mirrors LauncherBackupAgent#onRestoreFinished(): mark the restore pending and let
        // ModelDbController's normal DB-open path (RestoreDbTask#restoreIfNeeded, on the next
        // cold start after restartLauncher()) do the actual restore, same as a real Android
        // backup/restore. Calling RestoreDbTask#performRestore() directly here instead, on an
        // ad-hoc ModelDbController while the app was still running, skipped the
        // InvariantDeviceProfile reinit that only restoreIfNeeded() does - home-screen items
        // silently dropped (grid-bound), while app-drawer contents and settings didn't (not
        // grid-bound), until the same backup was restored a second time and that reinit had
        // already happened as an ordinary side effect of the launcher running in between.
        RestoreDbTask.setPending(context)

        if (contents.hasFlag(INCLUDE_LAYOUT_AND_SETTINGS)) {
            // Explicit safety net for grid size specifically, on top of the raw prefs-file replay
            // above: that replay is only as correct as the *backup's* copy of the classic prefs
            // XML file, and SharedPreferencesImpl's own apply() is asynchronous, so a backup made
            // immediately after changing grid size in Settings can race a not-yet-flushed write
            // and capture the previous value. info.gridState is captured into the backup's own
            // protobuf at create() time via a separate, synchronous path, so writing it here -
            // directly onto the live prefs instance, after the raw-file replay so it always wins -
            // guarantees the grid size actually matches what create() saw, regardless of whether
            // the raw prefs file happened to be fully flushed to disk at that moment.
            DeviceGridState(info.gridState).writeToPrefs(context, true)
        }
    }

    /**
     * Restores a classic SharedPreferences-backed backup entry (destined for [destFile]) by
     * staging its bytes under a name never before touched this process, reading that back as its
     * own fresh SharedPreferences instance - guaranteed to reflect the file on disk, since
     * nothing has it cached yet - and replaying every entry onto the real instance through its
     * own Editor. A raw byte copy onto destFile wouldn't do: SharedPreferencesImpl caches a
     * file's contents in memory the first time anything in this process reads it (virtually
     * certain just from the Settings UI being open), and always flushes that full in-memory copy
     * back to disk on its next write from anywhere, clobbering the raw-copied restore before the
     * process even gets a chance to restart.
     */
    private suspend fun restoreSharedPreferencesFile(destFile: File, sourceStream: InputStream) {
        destFile.parentFile?.mkdirs()
        val stagingName = "restore_staging_${destFile.nameWithoutExtension}_${System.nanoTime()}"
        val stagingFile = File(destFile.parentFile, "$stagingName.xml")
        sourceStream.copyTo(stagingFile.outputStream())
        try {
            val staged = context.getSharedPreferences(stagingName, Context.MODE_PRIVATE)
            context.getSharedPreferences(LauncherFiles.SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply {
                    staged.all.forEach { (key, value) ->
                        when (value) {
                            is Boolean -> putBoolean(key, value)

                            is Int -> putInt(key, value)

                            is Long -> putLong(key, value)

                            is Float -> putFloat(key, value)

                            is String -> putString(key, value)

                            is Set<*> ->
                                @Suppress("UNCHECKED_CAST")
                                putStringSet(key, value as Set<String>)
                        }
                    }
                }
                .apply()
        } finally {
            context.deleteSharedPreferences(stagingName)
        }
    }

    /**
     * Same staging trick as [restoreSharedPreferencesFile] and for the same reason, but DataStore
     * additionally throws if two DataStore instances for the same file are ever alive at once in
     * a process - so the real file's edit has to go through PreferenceManager2's own singleton
     * instance rather than a second ad-hoc one pointed at [destFile] directly.
     */
    private suspend fun restoreDataStoreFile(destFile: File, sourceStream: InputStream) {
        destFile.parentFile?.mkdirs()
        // PreferenceDataStoreFactory.create() requires the file name to end with the
        // "preferences_pb" extension - the timestamp has to go before it, not after, or this
        // throws IllegalStateException before ever reading the staged bytes back.
        val stagingFile = File(destFile.parentFile, "restore_staging_${System.nanoTime()}.preferences_pb")
        sourceStream.copyTo(stagingFile.outputStream())
        try {
            val stagedPrefs = PreferenceDataStoreFactory.create { stagingFile }.data.first()
            PreferenceManager2.getInstance(context).preferencesDataStore.edit { prefs ->
                prefs.clear()
                stagedPrefs.asMap().forEach { (key, value) ->
                    @Suppress("UNCHECKED_CAST")
                    prefs[key as Preferences.Key<Any>] = value
                }
            }
        } finally {
            stagingFile.delete()
        }
    }

    private suspend fun readZip(handlers: Map<String, suspend (InputStream) -> Unit>) {
        withContext(Dispatchers.IO) {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")!!
            pfd.use {
                FileInputStream(it.fileDescriptor).use { inStream ->
                    ZipInputStream(inStream).use { zipIs ->
                        var entry: ZipEntry?
                        while (true) {
                            entry = zipIs.nextEntry
                            if (entry == null) break
                            handlers[entry.name]?.invoke(zipIs)
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val BACKUP_VERSION = 1
        private const val PREFS_FILE_NAME = "${LauncherFiles.SHARED_PREFERENCES_KEY}.xml"
        private const val PREFS_DB_FILE_NAME = "preferences"
        private const val PREFS_DATASTORE_FILE_NAME = "preferences.preferences_pb"

        const val INFO_FILE_NAME = "info.pb"
        const val WALLPAPER_FILE_NAME = "wallpaper.png"
        const val SCREENSHOT_FILE_NAME = "screenshot.png"
        const val LAUNCHER_DB_FILE_NAME = "launcher.db"
        const val RESTORED_DB_FILE_NAME = "restored.db"

        const val INCLUDE_LAYOUT_AND_SETTINGS = 1 shl 0
        const val INCLUDE_WALLPAPER = 1 shl 1

        const val MIME_TYPE = "application/zip"
        val EXTRA_MIME_TYPES = arrayOf(MIME_TYPE, "application/x-zip", "application/octet-stream")

        val contentOptions = listOf(
            INCLUDE_LAYOUT_AND_SETTINGS to R.string.backup_content_layout_and_settings,
            INCLUDE_WALLPAPER to R.string.backup_content_wallpaper,
        )

        fun generateBackupFileName(): String {
            val fileName = "Lawnchair_Backup ${SimpleDateFormat.getDateTimeInstance().format(Date())}"
            return "$fileName.lawnchairbackup"
        }

        fun getFiles(context: Context, forRestore: Boolean): Map<String, File> {
            return mapOf(
                LAUNCHER_DB_FILE_NAME to launcherDbFile(context, forRestore),
                PREFS_FILE_NAME to prefsFile(context),
                PREFS_DB_FILE_NAME to prefsDbFile(context),
                PREFS_DATASTORE_FILE_NAME to prefsDataStoreFile(context),
            )
        }

        @SuppressLint("MissingPermission")
        suspend fun create(context: Context, contents: Int, screenshotBitmap: Bitmap, fileUri: Uri) {
            val idp = LauncherAppState.getIDP(context)
            val createdAt = Timestamp.newBuilder()
                .setSeconds(System.currentTimeMillis() / 1000)
            val colorHints = WallpaperManagerCompat.INSTANCE.get(context).wallpaperColors?.colorHints ?: 0
            val wallpaperSupportsDarkText = (colorHints and WallpaperColorsCompat.HINT_SUPPORTS_DARK_TEXT) != 0
            val info = BackupInfo.newBuilder()
                .setLawnchairVersion(BuildConfig.VERSION_CODE)
                .setBackupVersion(BACKUP_VERSION)
                .setCreatedAt(createdAt)
                .setContents(contents)
                .setGridState(DeviceGridState(idp).toProtoMessage())
                .setPreviewWidth(screenshotBitmap.width)
                .setPreviewHeight(screenshotBitmap.height)
                .setPreviewDarkText(wallpaperSupportsDarkText)
                .build()

            val pfd = context.contentResolver.openFileDescriptor(fileUri, "w")!!
            withContext(Dispatchers.IO) {
                pfd.use {
                    ZipOutputStream(FileOutputStream(pfd.fileDescriptor).buffered()).use { out ->
                        out.putNextEntry(ZipEntry(INFO_FILE_NAME))
                        info.writeTo(out)

                        if (contents.hasFlag(INCLUDE_WALLPAPER)) {
                            val wallpaperManager = WallpaperManager.getInstance(context)
                            val wallpaperBitmap = wallpaperManager.drawable?.toBitmap()
                            if (wallpaperBitmap != null) {
                                out.putNextEntry(ZipEntry(WALLPAPER_FILE_NAME))
                                wallpaperBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                            }
                        }
                        if (contents.hasFlag(INCLUDE_LAYOUT_AND_SETTINGS)) {
                            out.putNextEntry(ZipEntry(SCREENSHOT_FILE_NAME))
                            screenshotBitmap.compress(Bitmap.CompressFormat.PNG, 85, out)
                        }

                        getFiles(context, forRestore = false).entries.forEach {
                            if (!it.value.exists()) return@forEach
                            out.putNextEntry(ZipEntry(it.key))
                            it.value.inputStream().copyTo(out)
                        }
                    }
                }
            }
        }

        private fun launcherDbFile(context: Context, forRestore: Boolean): File {
            val dbName = if (forRestore) RESTORED_DB_FILE_NAME else LauncherAppState.getIDP(context).dbFile
            return context.getDatabasePath(dbName)
        }

        private fun prefsFile(context: Context): File {
            val dir = context.cacheDir.parent
            return File(dir, "shared_prefs/$PREFS_FILE_NAME")
        }

        private fun prefsDbFile(context: Context): File {
            return context.getDatabasePath(PREFS_DB_FILE_NAME)
        }

        private fun prefsDataStoreFile(context: Context): File {
            return File(context.filesDir, "datastore/${PREFS_DATASTORE_FILE_NAME}")
        }
    }
}
