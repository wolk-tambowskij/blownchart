package app.blownchart.ui.util

import com.android.launcher3.BuildConfig

fun isPlayStoreFlavor(): Boolean = BuildConfig.FLAVOR_channel == "play"
