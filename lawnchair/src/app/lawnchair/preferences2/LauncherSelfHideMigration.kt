/*
 * Copyright 2026, Lawnchair
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

package app.lawnchair.preferences2

import android.content.ComponentName
import android.content.Context
import android.os.Process
import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.lawnchair.LawnchairLauncher
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
        ComponentKey(ComponentName(context, LawnchairLauncher::class.java), Process.myUserHandle()).toString()
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
