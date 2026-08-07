package app.lawnchair.gestures.handlers

import android.content.Context
import app.lawnchair.BlownChartLauncher

class OpenAppSearchGestureHandler(context: Context) : OpenAppDrawerGestureHandler(context) {

    override suspend fun onTrigger(launcher: BlownChartLauncher) {
        super.onTrigger(launcher)
        launcher.appsView.searchUiManager.editText?.showKeyboard()
    }
}
