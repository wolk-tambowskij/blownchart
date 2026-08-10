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
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import app.blownchart.gestures.handlers.RecentsBounceActivity
import app.blownchart.preferences2.PreferenceManager2
import com.patrykmichalik.opto.core.firstBlocking

class BlownChartAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        // TEST: on firmware where BlownChart isn't config_recentsComponentName, watch that
        // component's window so we can catch the physical Recents button/gesture (which the OS
        // routes there directly, bypassing our own gesture handling entirely) and redirect
        // through RecentsBounceActivity - see RecentsGestureHandler for why routing through it
        // matters. Only watches that one package, not everything.
        val recentsComponent = blownChartApp.systemRecentsComponentName
        Log.i(TAG, "onServiceConnected: recentsComponent=$recentsComponent")
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = if (recentsComponent != null) AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED else 0
            packageNames = recentsComponent?.let { arrayOf(it.packageName) } ?: emptyArray()
            notificationTimeout = 0
            // TEST: also try to consume KEYCODE_APP_SWITCH directly in onKeyEvent below, before
            // the OS's own (already-buggy) handling of the physical button ever runs - if that
            // works on this firmware, it sidesteps the window-watching redirect above entirely
            // for the button case, along with the self-trigger cooldown tuning it needs. Left
            // enabled unconditionally alongside the window watcher (harmless no-op if this
            // firmware never actually delivers the key here).
            if (recentsComponent != null) flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        }
        blownChartApp.accessibilityService = this
    }

    override fun onDestroy() {
        blownChartApp.accessibilityService = null
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onInterrupt() {}

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_APP_SWITCH) return super.onKeyEvent(event)
        if (BlownChartApp.isRecentsEnabled) return super.onKeyEvent(event)
        if (!PreferenceManager2.getInstance(this).recentsButtonInterception.firstBlocking()) return super.onKeyEvent(event)

        // Consume both DOWN and UP so the OS's own (buggy) handling of this key never runs at
        // all, rather than reacting after the fact to a window it already half-opened - launch
        // directly on UP, same as a normal button release.
        if (event.action == KeyEvent.ACTION_UP) {
            Log.i(TAG, "onKeyEvent: consuming APP_SWITCH, launching bounce activity directly")
            launchRecentsBounceActivity()
        }
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        Log.i(TAG, "onAccessibilityEvent: pkg=${event.packageName} cls=${event.className} t=${SystemClock.elapsedRealtime()}")
        if (BlownChartApp.isRecentsEnabled) return
        val recentsComponent = blownChartApp.systemRecentsComponentName ?: return
        // Matched on the full component, not just the package: that package can host other
        // windows too (e.g. system UI surfaces unrelated to Recents), and matching on package
        // alone caught those as well, causing spurious redirects that had nothing to do with the
        // Recents button/gesture.
        if (event.packageName?.toString() != recentsComponent.packageName) return
        if (event.className?.toString() != recentsComponent.className) return
        Log.i(TAG, "onAccessibilityEvent: matched recentsComponent")

        // RecentsBounceActivity firing GLOBAL_ACTION_RECENTS itself makes this same window
        // reappear - without this cooldown, that self-caused reappearance would immediately fire
        // this same event handler again. Stamped from RecentsBounceActivity itself (shared with
        // the gesture path), not just here, since that's the actual call that causes the window
        // to reappear regardless of which path launched the bounce activity in the first place.
        //
        // 1500ms wasn't long enough: logs from real hardware show the underlying
        // recentsComponent - already known to be buggy, which is the entire reason this bounce
        // trick exists - keeps emitting its own WINDOW_STATE_CHANGED roughly every 1.8-2.3s all
        // on its own, well after that window, with no further user input. Each one got treated
        // as a fresh press and redirected again, producing an unprompted, self-perpetuating loop.
        // 5s comfortably clears that gap without being so long it would swallow a genuine second
        // press.
        val now = SystemClock.elapsedRealtime()
        val sinceSelfTrigger = now - blownChartApp.lastRecentsSelfTriggerAtMs
        if (sinceSelfTrigger < SELF_TRIGGER_COOLDOWN_MS) {
            Log.i(TAG, "onAccessibilityEvent: suppressed by cooldown, sinceSelfTrigger=${sinceSelfTrigger}ms")
            return
        }

        if (!PreferenceManager2.getInstance(this).recentsButtonInterception.firstBlocking()) {
            Log.i(TAG, "onAccessibilityEvent: recentsButtonInterception pref is off")
            return
        }

        // The window event we just matched is the OS's own attempt to open recentsComponent from
        // the physical button - already known to render incorrectly, which is why we redirect at
        // all. Logs show that attempt is still unwinding internally (a SnapshotStartingWindow
        // artifact, then focus landing back on whatever was open before the button was pressed)
        // for a few hundred ms after it starts. Launching RecentsBounceActivity and firing our
        // own GLOBAL_ACTION_RECENTS while that's still in flight overlapped the two attempts and
        // the transition aborted back to the previous app - it never happens on the gesture path,
        // which never has a competing natural attempt to begin with. Waiting for it to settle
        // before we intervene fixed this (confirmed on real hardware).
        //
        // Tried also sending GLOBAL_ACTION_BACK immediately on this same event, hoping to cancel
        // the natural attempt sooner and more deterministically than just waiting it out - but a
        // log showed this same event also fires from a recentsComponent window that's ALREADY
        // open and working (its own subsequent state-change events, not just the initial broken
        // one), and BACK doesn't distinguish the two: it dismissed sessions that were already
        // succeeding, including our own, at least as often as it cancelled a genuinely broken one.
        // Net worse than just waiting. Removed.
        Log.i(TAG, "onAccessibilityEvent: redirecting through RecentsBounceActivity after settle delay")
        handler.removeCallbacks(delayedLaunch)
        handler.postDelayed(delayedLaunch, NATURAL_ATTEMPT_SETTLE_DELAY_MS)
    }

    private val delayedLaunch = Runnable { launchRecentsBounceActivity() }

    private fun launchRecentsBounceActivity() {
        blownChartApp.lastRecentsSelfTriggerAtMs = SystemClock.elapsedRealtime()
        startActivity(
            Intent(this, RecentsBounceActivity::class.java)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION,
                ),
        )
    }

    companion object {
        private const val TAG = "BlownChartRecents"
        private const val SELF_TRIGGER_COOLDOWN_MS = 5000L

        // TEST: the natural (broken) attempt this event represents has, in every log capture so
        // far, fully unwound (window flash, then fallback to whatever was open before) within a
        // few hundred ms on its own. 500ms is a guess at a safe margin past that, unconfirmed.
        private const val NATURAL_ATTEMPT_SETTLE_DELAY_MS = 500L
    }
}
