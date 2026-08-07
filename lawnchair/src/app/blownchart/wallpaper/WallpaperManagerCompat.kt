package app.blownchart.wallpaper

import android.app.WallpaperManager
import android.content.Context
import app.blownchart.data.wallpaper.service.WallpaperService
import app.blownchart.util.MainThreadInitializedObject
import app.blownchart.util.requireSystemService
import app.blownchart.wallpaper.WallpaperColorsCompat.Companion.HINT_SUPPORTS_DARK_THEME
import com.android.launcher3.Utilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

sealed class WallpaperManagerCompat(val context: Context) {

    private val listeners = mutableListOf<OnColorsChangedListener>()
    private val colorHints: Int get() = wallpaperColors?.colorHints ?: 0
    val wallpaperManager: WallpaperManager = context.requireSystemService()
    val service = WallpaperService(context)

    abstract val wallpaperColors: WallpaperColorsCompat?

    val supportsDarkTheme: Boolean get() = (colorHints and HINT_SUPPORTS_DARK_THEME) != 0

    fun addOnChangeListener(listener: OnColorsChangedListener) {
        listeners.add(listener)
    }

    fun removeOnChangeListener(listener: OnColorsChangedListener) {
        listeners.remove(listener)
    }

    protected fun notifyChange() {
        if (service.getTopWallpapers().isEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
                service.saveWallpaper(wallpaperManager)
            }
        }

        listeners.toTypedArray().forEach {
            it.onColorsChanged()
        }
    }

    interface OnColorsChangedListener {
        fun onColorsChanged()
    }

    companion object {

        @JvmField
        val INSTANCE = MainThreadInitializedObject { context ->
            when {
                Utilities.ATLEAST_S -> WallpaperManagerCompatVS(context)
                Utilities.ATLEAST_O_MR1 -> WallpaperManagerCompatVOMR1(context)
                else -> WallpaperManagerCompatVO(context)
            }
        }
    }
}
