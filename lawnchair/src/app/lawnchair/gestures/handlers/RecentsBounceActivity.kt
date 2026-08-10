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
import android.app.ActivityManager
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
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

    // A fully transparent 1x1 bitmap, not null: passing a null icon to TaskDescription leaves the
    // task icon unset rather than blank, and Android then falls back to the app's own launcher
    // icon for it - which is exactly what made a ghost card left behind in Recents (see onCreate)
    // look like a duplicate labeled as this app.
    private val blankTaskIcon: Bitmap by lazy { Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // On firmware that doesn't honor excludeFromRecents/finishAndRemoveTask - already
        // established to be the case for the class of vendor Recents implementation this whole
        // feature targets - a card for this activity can be left behind in Recents even after its
        // task is gone, and on Android 11+ that card generally can't be force-removed at all.
        // Tapping it then resurrects a brand new instance of this bare, transparent,
        // non-interactive activity via its cached launch intent - nothing behind it to show, and
        // nothing on it to tap. RecentsGestureHandler/LawnchairAccessibilityService always stamp
        // lastRecentsBounceActivityLaunchedAtMs immediately before starting this activity, so a
        // long gap since that stamp means this onCreate() wasn't one of those - it's exactly that
        // resurrection. Bail out to the home screen instead of running the normal
        // suppress-snapshot-then-trigger-Recents flow, which has nothing left to usefully do here.
        val sinceLegitimateLaunch = SystemClock.elapsedRealtime() - lawnchairApp.lastRecentsBounceActivityLaunchedAtMs
        if (sinceLegitimateLaunch > STALE_RESURRECTION_THRESHOLD_MS) {
            Log.i(TAG, "onCreate: stale resurrection (sinceLegitimateLaunch=${sinceLegitimateLaunch}ms), bouncing home")
            startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            finishAndRemoveTask()
            return
        }
        // This activity is on screen for well under TRIGGER_DELAY_MS, but the OS still snapshots
        // it for its own Recents-list card the instant it stops being focused - and since it's
        // fully transparent, that snapshot is whatever was drawn behind it (the app that was open
        // before the button/gesture fired), which reads as a confusing duplicate of that app's
        // own card. On a well-behaved firmware, setRecentsScreenshotEnabled(false) alone would
        // replace this card with a blank placeholder - but on the class of vendor Recents
        // implementation this whole feature targets, neither excludeFromRecents (manifest) nor
        // the standard snapshot-suppression API are reliably respected. FLAG_SECURE is applied
        // unconditionally alongside it, not instead of it, as a second, lower-level line of
        // defense: it's enforced by the display compositor itself (SurfaceFlinger), which even a
        // custom vendor Recents renderer generally can't route around the way it can ignore an
        // ActivityTaskManager-level API or manifest attribute.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(false)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        // Blank label AND icon: TaskDescription(" ") alone only blanks the label - the task's icon
        // still defaults to this app's own launcher icon, which is exactly what made a ghost card
        // in Recents look like a duplicate labeled as this app instead of a blank placeholder.
        setTaskDescription(ActivityManager.TaskDescription(" ", blankTaskIcon))
    }

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

        // Comfortably larger than TRIGGER_DELAY_MS plus ordinary IPC/scheduling slack on a slow
        // device, but far short of how long a stale Recents card would realistically sit before a
        // user taps it - a real tap on a resurrected ghost card happens well after Recents is
        // already fully shown and settled, not within a couple of seconds of the original
        // trigger.
        private const val STALE_RESURRECTION_THRESHOLD_MS = 3000L
    }
}
