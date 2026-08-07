package app.blownchart.backup

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.graphics.drawable.toBitmap
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.blownchart.BlownChartProto.BackupInfo
import app.blownchart.data.AppDatabase
import app.blownchart.util.hasFlag
import app.blownchart.util.scaleDownTo
import app.blownchart.util.scaleDownToDisplaySize
import app.blownchart.wallpaper.WallpaperColorsCompat
import app.blownchart.wallpaper.WallpaperManagerCompat
import com.android.launcher3.BuildConfig
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherFiles
import com.android.launcher3.R
import com.android.launcher3.model.DeviceGridState
import com.android.launcher3.model.ModelDbController
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
import kotlinx.coroutines.withContext

class BlownChartBackup(
    private val context: Context,
    private val uri: Uri,
) {
    lateinit var info: BackupInfo
    var screenshot: Bitmap? = null
    var wallpaper: Bitmap? = null
    var lockWallpaper: Bitmap? = null

    suspend fun readInfoAndPreview() {
        var tmpScreenshot: Bitmap? = null
        var tmpWallpaper: Bitmap? = null
        var tmpLockWallpaper: Bitmap? = null
        readZip(
            mapOf(
                INFO_FILE_NAME to { info = BackupInfo.newBuilder().mergeFrom(it).build() },
                SCREENSHOT_FILE_NAME to { tmpScreenshot = BitmapFactory.decodeStream(it) },
                WALLPAPER_FILE_NAME to { tmpWallpaper = BitmapFactory.decodeStream(it) },
                LOCK_WALLPAPER_FILE_NAME to { tmpLockWallpaper = BitmapFactory.decodeStream(it) },
            ),
        )
        val size = max(info.previewWidth, info.previewHeight).coerceAtMost(4000)
        screenshot = tmpScreenshot?.scaleDownTo(size)
        wallpaper = tmpWallpaper?.scaleDownToDisplaySize(context)
        lockWallpaper = tmpLockWallpaper?.scaleDownToDisplaySize(context)
    }

    suspend fun restore(selectedContents: Int) {
        val handlers = mutableMapOf<String, suspend (InputStream) -> Unit>()
        val contents = selectedContents and info.contents
        if (contents.hasFlag(INCLUDE_LAYOUT_AND_SETTINGS)) {
            handlers.putAll(
                getFiles(context, forRestore = true).mapValues { entry ->
                    {
                        val file = entry.value
                        file.parentFile?.mkdirs()
                        it.copyTo(file.outputStream())
                    }
                },
            )
        }
        if (contents.hasFlag(INCLUDE_WALLPAPER)) {
            handlers[WALLPAPER_FILE_NAME] = {
                val wallpaperManager = WallpaperManager.getInstance(context)
                wallpaperManager.setBitmap(BitmapFactory.decodeStream(it))
            }
        }
        if (contents.hasFlag(INCLUDE_LOCK_WALLPAPER)) {
            // Only present in the zip if the device had a lock-screen wallpaper distinct from
            // the home one when the backup was made (see the LOCK_WALLPAPER_FILE_NAME check in
            // create()) - if this handler never fires, the lock screen just inherits whatever
            // the INCLUDE_WALLPAPER restore above set it to, same as before this flag existed.
            // Zip entries are processed in the order create() wrote them, so this always runs
            // after the home wallpaper's, letting it independently overwrite just the lock side.
            handlers[LOCK_WALLPAPER_FILE_NAME] = {
                val wallpaperManager = WallpaperManager.getInstance(context)
                wallpaperManager.setBitmap(BitmapFactory.decodeStream(it), null, true, WallpaperManager.FLAG_LOCK)
            }
        }
        context.getDatabasePath(LAUNCHER_DB_FILE_NAME).parentFile?.deleteRecursively()
        DeviceGridState(info.gridState).writeToPrefs(context, true)
        readZip(handlers)

        var dbController = ModelDbController(context)
        RestoreDbTask.performRestore(context, dbController)
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

        // Same key names as PreferenceManager2's settingsLockEnabled/settingsLockPinHash/
        // settingsLockBiometricEnabled - redefined here (rather than imported) since
        // PreferenceManager2 only exposes its opto Preference wrappers, not the raw DataStore
        // keys these need to redact a settings-lock-free copy of the DataStore file for backup.
        private val SETTINGS_LOCK_ENABLED_KEY = booleanPreferencesKey("settings_lock_enabled")
        private val SETTINGS_LOCK_PIN_HASH_KEY = stringPreferencesKey("settings_lock_pin_hash")
        private val SETTINGS_LOCK_BIOMETRIC_ENABLED_KEY = booleanPreferencesKey("settings_lock_biometric_enabled")

        const val INFO_FILE_NAME = "info.pb"
        const val WALLPAPER_FILE_NAME = "wallpaper.png"
        const val LOCK_WALLPAPER_FILE_NAME = "lock_wallpaper.png"
        const val SCREENSHOT_FILE_NAME = "screenshot.png"
        const val LAUNCHER_DB_FILE_NAME = "launcher.db"
        const val RESTORED_DB_FILE_NAME = "restored.db"

        const val INCLUDE_LAYOUT_AND_SETTINGS = 1 shl 0
        const val INCLUDE_WALLPAPER = 1 shl 1
        const val INCLUDE_LOCK_WALLPAPER = 1 shl 2

        const val MIME_TYPE = "application/zip"
        val EXTRA_MIME_TYPES = arrayOf(MIME_TYPE, "application/x-zip", "application/octet-stream")

        val contentOptions = listOf(
            INCLUDE_LAYOUT_AND_SETTINGS to R.string.backup_content_layout_and_settings,
            INCLUDE_WALLPAPER to R.string.backup_content_wallpaper,
            INCLUDE_LOCK_WALLPAPER to R.string.backup_content_lock_wallpaper,
        )

        fun generateBackupFileName(): String {
            val fileName = "BlownChart_Backup ${SimpleDateFormat.getDateTimeInstance().format(Date())}"
            return "$fileName.blownbackup"
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
                .setBlownChartVersion(BuildConfig.VERSION_CODE)
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
                // The "preferences" Room db is WAL-mode; flush it to the main db file first so
                // the raw file copy below can't miss folder/icon-override/wallpaper writes
                // still sitting in the -wal file. checkpointSync() blocks the calling thread,
                // so it must run here (Dispatchers.IO), not on whatever thread called create().
                AppDatabase.INSTANCE.get(context).checkpointSync()

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
                        if (contents.hasFlag(INCLUDE_LOCK_WALLPAPER)) {
                            val wallpaperManager = WallpaperManager.getInstance(context)
                            // Null unless the lock screen has its own wallpaper distinct from
                            // the home one (and isn't a live wallpaper) - most devices default
                            // to sharing a single wallpaper, so there's often nothing to write
                            // here even with this option enabled.
                            wallpaperManager.getWallpaperFile(WallpaperManager.FLAG_LOCK)?.use { pfd ->
                                val lockWallpaperBitmap = BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor)
                                if (lockWallpaperBitmap != null) {
                                    out.putNextEntry(ZipEntry(LOCK_WALLPAPER_FILE_NAME))
                                    lockWallpaperBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                                }
                            }
                        }
                        if (contents.hasFlag(INCLUDE_LAYOUT_AND_SETTINGS)) {
                            out.putNextEntry(ZipEntry(SCREENSHOT_FILE_NAME))
                            screenshotBitmap.compress(Bitmap.CompressFormat.PNG, 85, out)
                        }

                        getFiles(context, forRestore = false).entries.forEach {
                            if (!it.value.exists()) return@forEach
                            if (it.key == PREFS_DATASTORE_FILE_NAME) {
                                writeRedactedDataStoreEntry(context, it.value, out)
                            } else {
                                out.putNextEntry(ZipEntry(it.key))
                                it.value.inputStream().copyTo(out)
                            }
                        }
                    }
                }
            }
        }

        /**
         * Writes [sourceDataStoreFile] into [out] as [PREFS_DATASTORE_FILE_NAME], with the
         * settings-lock PIN hash and its toggles stripped out first. The settings lock is
         * deliberately excluded from every backup path: restoring onto a new device should come
         * up unlocked, with the PIN set up again by hand, not silently carrying over the old
         * device's PIN hash and enabled state.
         */
        private suspend fun writeRedactedDataStoreEntry(context: Context, sourceDataStoreFile: File, out: ZipOutputStream) {
            val redactedFile = File(context.cacheDir, "backup_$PREFS_DATASTORE_FILE_NAME")
            sourceDataStoreFile.copyTo(redactedFile, overwrite = true)
            try {
                val redactedStore = PreferenceDataStoreFactory.create { redactedFile }
                redactedStore.edit { prefs ->
                    prefs.remove(SETTINGS_LOCK_ENABLED_KEY)
                    prefs.remove(SETTINGS_LOCK_PIN_HASH_KEY)
                    prefs.remove(SETTINGS_LOCK_BIOMETRIC_ENABLED_KEY)
                }
                out.putNextEntry(ZipEntry(PREFS_DATASTORE_FILE_NAME))
                redactedFile.inputStream().copyTo(out)
            } finally {
                redactedFile.delete()
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
