package app.blownchart.gestures.handlers

import android.content.Context
import app.blownchart.BlownChartLauncher
import app.blownchart.preferences2.PreferenceManager2
import app.blownchart.qsb.LawnQsbLayout

class OpenSearchGestureHandler(context: Context) : GestureHandler(context) {

    override suspend fun onTrigger(launcher: BlownChartLauncher) {
        val prefs = PreferenceManager2.getInstance(launcher)
        val searchProvider = LawnQsbLayout.getSearchProvider(launcher, prefs)
        searchProvider.launch(launcher)
    }
}
