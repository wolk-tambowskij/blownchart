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
        blownChartApp.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
    }

    override fun onResume() {
        super.onResume()
        handler.postDelayed(triggerRecents, TRIGGER_DELAY_MS)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(triggerRecents)
        finish()
    }

    companion object {
        private const val TRIGGER_DELAY_MS = 80L
    }
}
