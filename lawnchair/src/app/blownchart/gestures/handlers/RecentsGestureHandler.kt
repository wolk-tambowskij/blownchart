/*
 * Copyright 2021, Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.blownchart.gestures.handlers

import android.content.Context
import android.content.Intent
import android.provider.Settings
import app.blownchart.BlownChartLauncher
import app.blownchart.blownChartApp
import app.blownchart.views.ComposeBottomSheet
import com.android.launcher3.R

class RecentsGestureHandler(context: Context) : GestureHandler(context) {

    override suspend fun onTrigger(launcher: BlownChartLauncher) {
        val app = launcher.blownChartApp
        if (!app.isAccessibilityServiceBound()) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ComposeBottomSheet.show(launcher) {
                ServiceWarningDialog(
                    title = R.string.d2ts_recents_a11y_hint_title,
                    description = R.string.recents_a11y_hint,
                    settingsIntent = intent,
                ) { close(true) }
            }
            return
        }
        // Routes through the accessibility service's focus-grabbing overlay instead of calling
        // performGlobalAction(GLOBAL_ACTION_RECENTS) directly from the launcher: on firmware where
        // config_recentsComponentName points to a broken vendor Recents renderer, invoking it
        // directly from the launcher's own focused window makes the OS misinterpret it as a
        // dismiss rather than an open. See BlownChartAccessibilityService.bounceToRecents for the
        // full explanation.
        app.bounceToRecents()
    }
}
