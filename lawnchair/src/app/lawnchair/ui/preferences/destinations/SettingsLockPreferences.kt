package app.lawnchair.ui.preferences.destinations

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences2.asState
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.security.SettingsLockGate
import app.lawnchair.security.SettingsLockUnlockActivity
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.layout.ExpandAndShrink
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
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
