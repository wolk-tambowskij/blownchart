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
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.lawnchair.LawnchairLauncher
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
        ComponentKey(ComponentName(context, LawnchairLauncher::class.java), Process.myUserHandle()).toString()
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
