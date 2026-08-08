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

package app.blownchart

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import app.blownchart.gestures.handlers.RecentsBounceActivity
import app.blownchart.preferences2.PreferenceManager2
import com.patrykmichalik.opto.core.firstBlocking

class BlownChartAccessibilityService : AccessibilityService() {

    private var lastSelfTriggeredAtMs = 0L

    override fun onServiceConnected() {
        // TEST: on firmware where BlownChart isn't config_recentsComponentName, watch that
        // component's window so we can catch the physical Recents button/gesture (which the OS
        // routes there directly, bypassing our own gesture handling entirely) and redirect
        // through RecentsBounceActivity - see RecentsGestureHandler for why routing through it
        // matters. Only watches that one package, not everything.
        val recentsComponent = blownChartApp.systemRecentsComponentName
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = if (recentsComponent != null) AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED else 0
            packageNames = recentsComponent?.let { arrayOf(it.packageName) } ?: emptyArray()
            notificationTimeout = 0
        }
        blownChartApp.accessibilityService = this
    }

    override fun onDestroy() {
        blownChartApp.accessibilityService = null
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (BlownChartApp.isRecentsEnabled) return
        val recentsComponent = blownChartApp.systemRecentsComponentName ?: return
        if (event.packageName != recentsComponent.packageName) return

        // The redirect below opens that same component's window itself (correctly, per
        // RecentsBounceActivity) - without this cooldown, that self-triggered reopening would
        // immediately fire this same event handler again.
        val now = SystemClock.elapsedRealtime()
        if (now - lastSelfTriggeredAtMs < SELF_TRIGGER_COOLDOWN_MS) return

        if (!PreferenceManager2.getInstance(this).recentsButtonInterception.firstBlocking()) return

        lastSelfTriggeredAtMs = now
        startActivity(
            Intent(this, RecentsBounceActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    companion object {
        private const val SELF_TRIGGER_COOLDOWN_MS = 1500L
    }
}
