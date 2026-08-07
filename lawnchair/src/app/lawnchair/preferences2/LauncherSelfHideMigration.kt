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

package app.lawnchair.preferences2

import android.content.ComponentName
import android.content.Context
import android.os.Process
import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.lawnchair.BlownChartLauncher
import com.android.launcher3.util.ComponentKey

/**
 * Adds the launcher's own app entry to the hidden-apps set once, so it doesn't show up in the
 * drawer/app pickers like a regular installed app. Runs once ever (fresh installs and upgrades
 * alike) via the [appliedKey] flag, so a user who later un-hides it isn't fought by this on the
 * next launch.
 */
class LauncherSelfHideMigration(private val context: Context) : DataMigration<Preferences> {

    private val appliedKey = booleanPreferencesKey("self_app_hidden_seed_applied")
    private val hiddenAppsKey = stringSetPreferencesKey("hidden_apps")

    private val selfComponentKey by lazy {
        ComponentKey(ComponentName(context, BlownChartLauncher::class.java), Process.myUserHandle()).toString()
    }

    override suspend fun shouldMigrate(currentData: Preferences): Boolean = currentData[appliedKey] != true

    override suspend fun migrate(currentData: Preferences): Preferences {
        val hiddenApps = currentData[hiddenAppsKey] ?: emptySet()
        return currentData.toMutablePreferences().apply {
            this[hiddenAppsKey] = hiddenApps + selfComponentKey
            this[appliedKey] = true
        }.toPreferences()
    }

    override suspend fun cleanUp() {}
}
