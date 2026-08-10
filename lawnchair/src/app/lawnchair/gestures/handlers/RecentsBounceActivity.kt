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

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import app.lawnchair.lawnchairApp

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
        // LawnchairAccessibilityService needs to recognize as self-caused, regardless of whether
        // the gesture path or the button-watcher path is what got us here.
        lawnchairApp.lastRecentsSelfTriggerAtMs = SystemClock.elapsedRealtime()
        val result = lawnchairApp.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
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
        private const val TAG = "LawnchairRecents"

        // Long enough for this activity to finish resuming before firing the action; not for
        // waiting out a competing natural attempt, since neither caller (the accessibility
        // overlay tap, or the gesture handler) triggers one.
        private const val TRIGGER_DELAY_MS = 80L
    }
}
