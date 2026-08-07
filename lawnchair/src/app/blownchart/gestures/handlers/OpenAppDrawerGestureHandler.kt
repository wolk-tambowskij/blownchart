package app.blownchart.gestures.handlers

import android.content.Context
import app.blownchart.BlownChartLauncher
import app.blownchart.animateToAllApps

open class OpenAppDrawerGestureHandler(context: Context) : GestureHandler(context) {

    override suspend fun onTrigger(launcher: BlownChartLauncher) {
        launcher.animateToAllApps()
    }
}
