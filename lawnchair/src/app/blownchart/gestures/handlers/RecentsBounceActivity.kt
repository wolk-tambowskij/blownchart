/*
 *     Copyright (C) 2026 Wolk Tambowskij
 *
 *     This file is part of the BlownChart fork of Lawnchair Launcher.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package app.blownchart.gestures.handlers

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import app.blownchart.blownChartApp

/**
 * Transparent, non-HOME activity whose only job is to be the resumed foreground task when
 * GLOBAL_ACTION_RECENTS fires, instead of the launcher itself. On firmware where the system
 * Recents screen renders correctly from a regular app but flashes and disappears when invoked
 * directly from the home screen, briefly standing in as that "regular app" is the workaround.
 * Finishes itself as soon as it's no longer the foreground activity (i.e. once Recents has taken
 * over), so it never lingers as a real task.
 */
class RecentsBounceActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private val triggerRecents = Runnable {
        // Stamped right here, not by whichever caller launched this activity: this is the actual
        // call that makes the real Recents window reappear, so this is what
        // BlownChartAccessibilityService needs to recognize as self-caused, regardless of whether
        // the gesture path or the button-watcher path is what got us here.
        blownChartApp.lastRecentsSelfTriggerAtMs = SystemClock.elapsedRealtime()
        val result = blownChartApp.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
        Log.i(TAG, "triggerRecents: performGlobalAction result=$result t=${SystemClock.elapsedRealtime()}")
    }

    override fun onResume() {
        super.onResume()
        Log.i(TAG, "onResume t=${SystemClock.elapsedRealtime()}")
        handler.postDelayed(triggerRecents, TRIGGER_DELAY_MS)
    }

    override fun onPause() {
        super.onPause()
        Log.i(TAG, "onPause t=${SystemClock.elapsedRealtime()}")
        handler.removeCallbacks(triggerRecents)
        // finish() alone leaves the task around for the recents UI to pick up as the most
        // recently used entry on firmware that doesn't honor excludeFromRecents for a task that
        // was only ever briefly foregrounded; finishAndRemoveTask() drops the task itself instead
        // of just the activity, which is what actually keeps it out of that list.
        finishAndRemoveTask()
    }

    companion object {
        private const val TAG = "BlownChartRecents"
        private const val TRIGGER_DELAY_MS = 80L
    }
}
