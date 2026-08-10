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

package app.lawnchair

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
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
import app.lawnchair.gestures.handlers.RecentsBounceActivity
import app.lawnchair.preferences2.PreferenceManager2
import com.patrykmichalik.opto.core.firstBlocking

class LawnchairAccessibilityService : AccessibilityService() {

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
        lawnchairApp.accessibilityService = this
        updateOverlay()
    }

    override fun onDestroy() {
        lawnchairApp.accessibilityService = null
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
        if (LawnchairApp.isRecentsEnabled) {
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
                Log.i(TAG, "overlay: clicked, launching bounce activity")
                launchRecentsBounceActivity()
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

    private fun launchRecentsBounceActivity() {
        // Guards against rapid repeated overlay taps spawning overlapping bounce activities.
        val now = SystemClock.elapsedRealtime()
        val sinceSelfTrigger = now - lawnchairApp.lastRecentsSelfTriggerAtMs
        if (sinceSelfTrigger < SELF_TRIGGER_COOLDOWN_MS) {
            Log.i(TAG, "launchRecentsBounceActivity: suppressed by cooldown, sinceSelfTrigger=${sinceSelfTrigger}ms")
            return
        }
        lawnchairApp.lastRecentsSelfTriggerAtMs = now
        lawnchairApp.lastRecentsBounceActivityLaunchedAtMs = now
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
        private const val TAG = "LawnchairRecents"
        private const val SELF_TRIGGER_COOLDOWN_MS = 800L
        private const val OVERLAY_UPDATE_DEBOUNCE_MS = 150L
        private const val SYSTEMUI_RECENTS_VIEW_ID = "com.android.systemui:id/recent_apps"
    }
}
