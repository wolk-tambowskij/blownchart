package app.blownchart.hotseat

import android.content.Context
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import app.blownchart.util.isPackageInstalledAndEnabled
import com.android.launcher3.R

sealed class HotseatMode(
    @StringRes val nameResourceId: Int,
    @LayoutRes val layoutResourceId: Int,
) {
    companion object {
        fun fromString(value: String): HotseatMode = when (value) {
            "disabled" -> DisabledHotseat
            "google_search" -> GoogleSearchHotseat
            else -> BlownChartHotseat
        }

        /**
         * @return The list of all hot seat modes.
         */
        fun values() = listOf(
            DisabledHotseat,
            BlownChartHotseat,
            GoogleSearchHotseat,
        )
    }

    abstract fun isAvailable(context: Context): Boolean
}

object BlownChartHotseat : HotseatMode(
    nameResourceId = R.string.hotseat_mode_blownchart,
    layoutResourceId = R.layout.search_container_hotseat,
) {
    override fun toString() = "lawnchair"
    override fun isAvailable(context: Context): Boolean = true
}

object GoogleSearchHotseat : HotseatMode(
    nameResourceId = R.string.hotseat_mode_google_search,
    layoutResourceId = R.layout.search_container_hotseat_google_search,
) {
    override fun toString(): String = "google_search"

    override fun isAvailable(context: Context): Boolean = context.packageManager.isPackageInstalledAndEnabled("com.google.android.googlequicksearchbox")
}

object DisabledHotseat : HotseatMode(
    nameResourceId = R.string.hotseat_mode_disabled,
    layoutResourceId = R.layout.empty_view,
) {
    override fun toString(): String = "disabled"

    override fun isAvailable(context: Context): Boolean = true
}
