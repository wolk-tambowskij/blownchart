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
import android.app.ActivityOptions
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import app.blownchart.preferences2.PreferenceManager2
import com.patrykmichalik.opto.core.firstBlocking

class BlownChartAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val windowManager by lazy { getSystemService(WindowManager::class.java)!! }

    private var overlayView: View? = null
    private var overlayBounds: Rect? = null

    override fun onServiceConnected() {
        // Watches TYPE_WINDOWS_CHANGED, unrestricted by package (nav bar layout changes aren't
        // tied to any single package) - see updateOverlay() below for why.
        Log.i(TAG, "onServiceConnected")
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOWS_CHANGED
            packageNames = null
            notificationTimeout = 0
            // FLAG_RETRIEVE_INTERACTIVE_WINDOWS: needed for getWindows() below to actually return
            // the navigation bar's window/content instead of nothing. FLAG_REPORT_VIEW_IDS: needed
            // for findAccessibilityNodeInfosByViewId() below to have anything to match against -
            // without it every node's view id comes back empty and that lookup silently always
            // returns nothing.
            flags = flags or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        blownChartApp.accessibilityService = this
        updateOverlay()
    }

    override fun onDestroy() {
        blownChartApp.accessibilityService = null
        removeOverlay()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Rotation changes the nav bar's orientation/position; TYPE_WINDOWS_CHANGED usually covers
        // this too, but firmwares are inconsistent about firing it for rotation specifically, so
        // this is a direct, guaranteed-to-fire backstop.
        updateOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return
        // Fires for window changes system-wide, not just the nav bar - debounced since a single
        // button press/screen transition can trigger a burst of these, and each one would
        // otherwise walk the nav bar's node tree again for no reason.
        handler.removeCallbacks(debouncedUpdateOverlay)
        handler.postDelayed(debouncedUpdateOverlay, OVERLAY_UPDATE_DEBOUNCE_MS)
    }

    // ---- Overlay: intercept the physical Recents button's touch before SystemUI ever sees it ----

    private val debouncedUpdateOverlay = Runnable { updateOverlay() }

    private fun updateOverlay() {
        if (BlownChartApp.isRecentsEnabled) {
            removeOverlay()
            return
        }
        if (!PreferenceManager2.getInstance(this).recentsButtonInterception.firstBlocking()) {
            removeOverlay()
            return
        }
        if (isGestureNavigationEnabled()) {
            // No on-screen physical Recents button exists in gesture-nav mode - nothing to
            // overlay.
            removeOverlay()
            return
        }
        val bounds = findRecentsButtonBounds()
        if (bounds == null) {
            // Leaves any existing overlay in place at its last known position rather than tearing
            // it down - a transient failure to find the button (e.g. nav bar briefly hidden by an
            // immersive app) shouldn't blind us until the next successful lookup.
            Log.i(TAG, "updateOverlay: recents button not found")
            return
        }
        if (bounds == overlayBounds) return
        addOrMoveOverlay(bounds)
    }

    private fun isGestureNavigationEnabled(): Boolean {
        // Standard Settings.Secure key, readable by any app without special permission.
        // 0 = 3-button nav, 1 = 2-button (deprecated pill), 2 = gesture nav.
        val navMode = Settings.Secure.getInt(contentResolver, "navigation_mode", 0)
        return navMode == 2
    }

    private fun findRecentsButtonBounds(): Rect? {
        val windowList = try {
            windows
        } catch (e: SecurityException) {
            Log.i(TAG, "findRecentsButtonBounds: no permission to read windows: $e")
            return null
        }
        for (window in windowList) {
            // AccessibilityWindowInfo has no dedicated navigation-bar type - the public API only
            // distinguishes as far as TYPE_SYSTEM, which the nav bar shares with other system
            // windows (status bar, etc). Searching all of them for the recents view id/description
            // is cheap (each of these trees is tiny) and doesn't depend on a type that doesn't
            // exist.
            if (window.type != AccessibilityWindowInfo.TYPE_SYSTEM) continue
            val root = window.root ?: continue
            val node = root.findAccessibilityNodeInfosByViewId(SYSTEMUI_RECENTS_VIEW_ID).firstOrNull()
                ?: findRecentsButtonByDescription(root)
            if (node != null) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                if (!bounds.isEmpty) return bounds
            }
        }
        return null
    }

    private fun findRecentsButtonByDescription(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // Fallback for firmware that renamed/dropped the standard AOSP view id: the nav bar's node
        // tree is tiny (a handful of buttons), so a plain BFS by content description is cheap and
        // matches whatever locale is active.
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val description = node.contentDescription?.toString()?.lowercase()
            if (description != null && (description.contains("recent") || description.contains("overview"))) {
                return node
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
        }
        return null
    }

    private fun addOrMoveOverlay(bounds: Rect) {
        val view = overlayView ?: View(this).apply {
            setOnClickListener {
                Log.i(TAG, "overlay: clicked, bouncing to Recents")
                bounceToRecents()
            }
        }.also { overlayView = it }

        val params = WindowManager.LayoutParams(
            bounds.width(),
            bounds.height(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // NOT_FOCUSABLE: don't steal keyboard/back handling. NOT_TOUCH_MODAL: without this, a
            // touchable window claims ALL pointer events system-wide, not just within its own
            // bounds - omitting it would have silently broken touch everywhere else on screen
            // while this overlay is up.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = bounds.left
            y = bounds.top
        }

        try {
            if (view.isAttachedToWindow) {
                windowManager.updateViewLayout(view, params)
            } else {
                windowManager.addView(view, params)
            }
            overlayBounds = bounds
            Log.i(TAG, "overlay: positioned at $bounds")
        } catch (e: Exception) {
            Log.i(TAG, "overlay: failed to add/update: $e")
        }
    }

    private fun removeOverlay() {
        val view = overlayView ?: return
        try {
            if (view.isAttachedToWindow) windowManager.removeView(view)
        } catch (e: Exception) {
            Log.i(TAG, "overlay: failed to remove: $e")
        }
        overlayView = null
        overlayBounds = null
    }

    // ---- Bounce-to-Recents: resume the real last-used app, then trigger Recents from there ----

    /**
     * On firmware where calling performGlobalAction(GLOBAL_ACTION_RECENTS) directly from the
     * launcher's own focused window gets misinterpreted by the OS as a dismiss rather than an
     * open, this briefly resumes whichever real app the user had open before the launcher, then
     * fires the action from there instead.
     *
     * An invisible, focusable TYPE_ACCESSIBILITY_OVERLAY window (tried first) turned out not to
     * be enough on real hardware - WindowManagerService's focus bookkeeping for that window type
     * apparently isn't equivalent to a real app actually being resumed. Resuming a genuine,
     * already-existing task via FLAG_ACTIVITY_REORDER_TO_FRONT sidesteps that entirely: there is
     * nothing fake about it from the OS's perspective, and since it's an *existing* task being
     * reordered rather than a new one being created, it doesn't add anything new to Recents either
     * - no new Task means no new card, the same guarantee the overlay attempt was going for.
     *
     * Falls back to calling performGlobalAction directly (the original, occasionally-misbehaving
     * behavior) when there's no eligible last app to resume - freshly booted device, all recent
     * history purged, or PACKAGE_USAGE_STATS not granted. That's a strict regression only in that
     * one narrow edge case, not a step backward from where this feature started.
     */
    fun bounceToRecents() {
        // Guards against rapid repeated triggers (overlay tap and gesture landing close together)
        // spawning overlapping attempts.
        val now = SystemClock.elapsedRealtime()
        val sinceSelfTrigger = now - blownChartApp.lastRecentsSelfTriggerAtMs
        if (sinceSelfTrigger < SELF_TRIGGER_COOLDOWN_MS) {
            Log.i(TAG, "bounceToRecents: suppressed by cooldown, sinceSelfTrigger=${sinceSelfTrigger}ms")
            return
        }
        val lastAppPackage = findLastForegroundPackage()
        val launchIntent = lastAppPackage?.let { packageManager.getLaunchIntentForPackage(it) }
        if (launchIntent == null) {
            Log.i(TAG, "bounceToRecents: no eligible last app (lastAppPackage=$lastAppPackage), triggering Recents directly")
            triggerRecents()
            return
        }
        Log.i(TAG, "bounceToRecents: resuming $lastAppPackage before triggering Recents")
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        try {
            // Zero-length custom animation: this app is only ever meant to be resumed for a
            // moment on its way to Recents, not actually shown to the user.
            startActivity(launchIntent, ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle())
        } catch (e: Exception) {
            Log.i(TAG, "bounceToRecents: failed to resume $lastAppPackage: $e")
            triggerRecents()
            return
        }
        handler.postDelayed({ triggerRecents() }, RESUME_SETTLE_DELAY_MS)
    }

    private fun triggerRecents() {
        blownChartApp.lastRecentsSelfTriggerAtMs = SystemClock.elapsedRealtime()
        val result = performGlobalAction(GLOBAL_ACTION_RECENTS)
        Log.i(TAG, "triggerRecents: performGlobalAction result=$result")
    }

    /**
     * The package of the most recently foregrounded app other than this launcher itself and
     * SystemUI, per [UsageEvents] - or null if none was found in the lookback window, or usage
     * access isn't granted. Uses the raw event stream (not [UsageStatsManager]'s daily-bucketed
     * aggregate query) since that's what actually preserves correct chronological ordering for
     * "what was the very last app" rather than coarse per-interval totals.
     */
    private fun findLastForegroundPackage(): String? {
        if (!hasUsageStatsAccess()) {
            Log.i(TAG, "findLastForegroundPackage: usage access not granted")
            return null
        }
        val usageStatsManager = getSystemService(UsageStatsManager::class.java) ?: return null
        val end = System.currentTimeMillis()
        val begin = end - LAST_APP_LOOKBACK_MS
        val events = usageStatsManager.queryEvents(begin, end)
        val event = UsageEvents.Event()
        var lastPackage: String? = null
        var lastTimestamp = 0L
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            // MOVE_TO_FOREGROUND covers API 26-28 (this app's minSdk); ACTIVITY_RESUMED is the
            // more precise successor added in API 29, checked in addition to it, not instead.
            if (event.eventType != UsageEvents.Event.ACTIVITY_RESUMED &&
                event.eventType != UsageEvents.Event.MOVE_TO_FOREGROUND
            ) {
                continue
            }
            if (event.packageName == packageName || event.packageName == SYSTEMUI_PACKAGE) continue
            if (event.timeStamp >= lastTimestamp) {
                lastTimestamp = event.timeStamp
                lastPackage = event.packageName
            }
        }
        return lastPackage
    }

    /**
     * Whether this app currently has usage-access ("Usage access" in Settings) granted. Despite
     * PACKAGE_USAGE_STATS being declared as a normal manifest permission, third-party apps are
     * actually gated on it through [AppOpsManager], not the regular permission-grant system -
     * [checkCallingOrSelfPermission] for this specific permission reliably returns DENIED
     * regardless of whether the user has actually enabled it, since its declared protection
     * level was never meant to be satisfied by a normal grant dialog in the first place.
     */
    private fun hasUsageStatsAccess(): Boolean {
        val appOps = getSystemService(AppOpsManager::class.java) ?: return false

        @Suppress("DEPRECATION")
        val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    companion object {
        private const val TAG = "BlownChartRecents"

        // Was 5000ms, tuned for a now-removed fallback that had to survive the buggy
        // recentsComponent re-firing its own window-state-changed event every 1.8-2.3s on its
        // own. bounceToRecents() is only ever called from a real touch on the button overlay or
        // the gesture handler now, so this only needs to de-dupe a single physical tap (e.g. a
        // bouncy touchscreen firing two click events back to back), not block a genuine second
        // press for multiple seconds. A 5s lockout after every trigger was a likely cause of the
        // button "not always working on the first try."
        private const val SELF_TRIGGER_COOLDOWN_MS = 800L
        private const val OVERLAY_UPDATE_DEBOUNCE_MS = 150L
        private const val SYSTEMUI_RECENTS_VIEW_ID = "com.android.systemui:id/recent_apps"
        private const val SYSTEMUI_PACKAGE = "com.android.systemui"

        // How far back to look for the last-used app. Generous on purpose: the cost of a wider
        // window is scanning a few more events, not correctness - the loop below always ends up
        // with the chronologically last eligible one regardless of window size.
        private const val LAST_APP_LOOKBACK_MS = 60 * 60 * 1000L

        // Long enough for FLAG_ACTIVITY_REORDER_TO_FRONT to actually bring the target task's
        // window to the front (including a cold start if its process was no longer alive) before
        // firing Recents from it; short enough to keep the whole bounce feeling instantaneous.
        private const val RESUME_SETTLE_DELAY_MS = 50L
    }
}
