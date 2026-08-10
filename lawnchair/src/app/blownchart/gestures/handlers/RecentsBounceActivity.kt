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
    }

    override fun onStop() {
        super.onStop()
        Log.i(TAG, "onStop t=${SystemClock.elapsedRealtime()}")
        // Finishing here rather than in onPause: onPause fires as soon as focus is merely lost,
        // which can race the real Recents window's own appear transition while it's still only
        // partially drawn over this activity. On this firmware's already-flaky Recents renderer,
        // that race looked like Recents falling back to whatever was open before it was invoked.
        // onStop only fires once this activity is fully obscured, i.e. once Recents has actually
        // taken over - finishAndRemoveTask() here (rather than just finish()) additionally drops
        // the task itself, which is what keeps it out of the Recents list at all. Guarded since
        // onPause and onStop can both land close together and finishAndRemoveTask() isn't
        // idempotent-safe against being asked twice.
        if (!isFinishing) finishAndRemoveTask()
    }

    companion object {
        private const val TAG = "BlownChartRecents"

        // Was raised to 300ms while a since-removed fallback path could still land here racing a
        // natural (buggy) system attempt at opening Recents, and needed to wait that out first.
        // Both remaining callers (BlownChartAccessibilityService's overlay tap, and
        // RecentsGestureHandler) never trigger a competing natural attempt in the first place -
        // the overlay intercepts the touch before SystemUI ever sees it, and the gesture path
        // never touches the physical button at all - so there's nothing left to wait out. Back
        // to a minimal delay just to let this activity finish resuming before firing the action.
        private const val TRIGGER_DELAY_MS = 80L
    }
}
