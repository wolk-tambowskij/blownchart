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

package app.lawnchair.gestures.handlers

import android.content.Context
import android.content.Intent
import android.provider.Settings
import app.lawnchair.LawnchairLauncher
import app.lawnchair.lawnchairApp
import app.lawnchair.views.ComposeBottomSheet
import com.android.launcher3.R

class RecentsGestureHandler(context: Context) : GestureHandler(context) {

    override suspend fun onTrigger(launcher: LawnchairLauncher) {
        val app = launcher.lawnchairApp
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
        // Routes through RecentsBounceActivity instead of calling performGlobalAction directly
        // from the launcher: on firmware where config_recentsComponentName points to a broken
        // vendor Recents renderer, invoking it directly from the launcher's own resumed context
        // makes the OS flash it and immediately fall back to the launcher, as if a stray back
        // press had dismissed it. Standing in as a plain, non-launcher foreground activity first
        // works around that - see RecentsBounceActivity for the full explanation.
        launcher.startActivity(
            Intent(launcher, RecentsBounceActivity::class.java)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        // Without this, a launch that lands while a previous bounce activity's
                        // task hasn't fully torn down yet (same empty taskAffinity) could get
                        // added to that stale task instead of a fresh one.
                        Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION,
                ),
        )
    }
}
