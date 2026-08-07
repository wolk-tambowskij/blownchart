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

package app.blownchart.ui.preferences.destinations

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.blownchart.preferences.getAdapter
import app.blownchart.preferences2.asState
import app.blownchart.preferences2.preferenceManager2
import app.blownchart.security.SettingsLockGate
import app.blownchart.security.SettingsLockUnlockActivity
import app.blownchart.ui.preferences.components.controls.ClickablePreference
import app.blownchart.ui.preferences.components.controls.SwitchPreference
import app.blownchart.ui.preferences.components.layout.ExpandAndShrink
import app.blownchart.ui.preferences.components.layout.PreferenceGroup
import app.blownchart.ui.preferences.components.layout.PreferenceLayout
import com.android.launcher3.R

@Composable
fun SettingsLockPreferences(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs2 = preferenceManager2()
    val enabled by prefs2.settingsLockEnabled.asState()
    val biometricAdapter = prefs2.settingsLockBiometricEnabled.getAdapter()

    val setupPinLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        // On success, SettingsLockUnlockActivity already called SettingsLockGate.setPin(),
        // which flips settingsLockEnabled; `enabled` above updates on its own via the
        // DataStore-backed state. Nothing else to do here either way.
    }

    PreferenceLayout(
        label = stringResource(id = R.string.settings_lock_label),
        backArrowVisible = true,
        modifier = modifier,
    ) {
        PreferenceGroup(description = stringResource(id = R.string.settings_lock_description)) {
            SwitchPreference(
                checked = enabled,
                onCheckedChange = { turnOn ->
                    if (turnOn) {
                        setupPinLauncher.launch(SettingsLockUnlockActivity.createSetupIntent(context))
                    } else {
                        SettingsLockGate.disable(context)
                    }
                },
                label = stringResource(id = R.string.settings_lock_label),
            )
        }

        ExpandAndShrink(visible = enabled) {
            PreferenceGroup {
                ClickablePreference(
                    label = stringResource(id = R.string.settings_lock_change_pin_action),
                    onClick = { setupPinLauncher.launch(SettingsLockUnlockActivity.createSetupIntent(context)) },
                )
                SwitchPreference(
                    adapter = biometricAdapter,
                    label = stringResource(id = R.string.settings_lock_use_biometric_label),
                    description = stringResource(id = R.string.settings_lock_use_biometric_description),
                )
            }
        }
    }
}
