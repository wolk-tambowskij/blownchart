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

package app.blownchart.preferences2

import android.content.ComponentName
import android.content.Context
import android.os.Process
import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.blownchart.BlownChartLauncher
import com.android.launcher3.util.ComponentKey

/**
 * Adds the launcher's own app entry to the hidden-apps set once per component identity, so it
 * doesn't show up in the drawer/app pickers like a regular installed app. Keyed by
 * [selfComponentKey] rather than a plain boolean flag: a backup restore or reinstall can carry
 * over an "already applied" flag from a build with a different applicationId/component name
 * (e.g. debug vs release, or a pre-rebrand backup), which would otherwise permanently prevent the
 * seed from ever running again for the component that's actually installed now. Once applied for
 * the current component, a user who later un-hides it isn't fought by this on the next launch.
 */
class LauncherSelfHideMigration(private val context: Context) : DataMigration<Preferences> {

    private val appliedForKey = stringPreferencesKey("self_app_hidden_seed_applied_for")
    private val hiddenAppsKey = stringSetPreferencesKey("hidden_apps")

    private val selfComponentKey by lazy {
        ComponentKey(ComponentName(context, BlownChartLauncher::class.java), Process.myUserHandle()).toString()
    }

    override suspend fun shouldMigrate(currentData: Preferences): Boolean = currentData[appliedForKey] != selfComponentKey

    override suspend fun migrate(currentData: Preferences): Preferences {
        val hiddenApps = currentData[hiddenAppsKey] ?: emptySet()
        return currentData.toMutablePreferences().apply {
            this[hiddenAppsKey] = hiddenApps + selfComponentKey
            this[appliedForKey] = selfComponentKey
        }.toPreferences()
    }

    override suspend fun cleanUp() {}
}
