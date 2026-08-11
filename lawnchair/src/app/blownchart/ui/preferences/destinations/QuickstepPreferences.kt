package app.blownchart.ui.preferences.destinations

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TipsAndUpdates
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.blownchart.BlownChartApp
import app.blownchart.blownChartApp
import app.blownchart.preferences.getAdapter
import app.blownchart.preferences.observeAsState
import app.blownchart.preferences.preferenceManager
import app.blownchart.preferences2.preferenceManager2
import app.blownchart.ui.preferences.components.QuickActionsPreferences
import app.blownchart.ui.preferences.components.RecentsQuickAction
import app.blownchart.ui.preferences.components.RestrictedSettingsBanner
import app.blownchart.ui.preferences.components.controls.SliderPreference
import app.blownchart.ui.preferences.components.controls.SwitchPreference
import app.blownchart.ui.preferences.components.controls.WarningPreference
import app.blownchart.ui.preferences.components.layout.ExpandAndShrink
import app.blownchart.ui.preferences.components.layout.PreferenceGroup
import app.blownchart.ui.preferences.components.layout.PreferenceLayout
import app.blownchart.ui.preferences.components.layout.PreferenceTemplate
import app.blownchart.ui.util.preview.PreviewBlownChart
import app.blownchart.util.hasUsageStatsAccess
import app.blownchart.util.isDeviceAdminActive
import app.blownchart.util.isOnePlusStock
import app.blownchart.util.lifecycleState
import app.blownchart.util.openUsageAccessSettings
import app.blownchart.util.requestDeviceAdmin
import com.android.launcher3.R
import com.android.launcher3.Utilities

@Composable
fun QuickstepPreferences(
    modifier: Modifier = Modifier,
) {
    val prefs = preferenceManager()
    val prefs2 = preferenceManager2()
    val context = LocalContext.current
    val lensAvailable = remember {
        context.packageManager.getLaunchIntentForPackage("com.google.ar.lens") != null
    }

    val recentActionsPreferences: List<RecentsQuickAction> = listOfNotNull(
        RecentsQuickAction(
            id = 0,
            adapter = prefs.recentsActionScreenshot.getAdapter(),
            label = stringResource(id = R.string.action_screenshot),
        ).takeIf { !isOnePlusStock },
        RecentsQuickAction(
            id = 1,
            adapter = prefs.recentsActionShare.getAdapter(),
            label = stringResource(id = R.string.action_share),
        ),
        RecentsQuickAction(
            id = 2,
            adapter = prefs.recentsActionLens.getAdapter(),
            label = stringResource(id = R.string.action_lens),
        ).takeIf { lensAvailable },
        RecentsQuickAction(
            id = 3,
            adapter = prefs.recentsActionLocked.getAdapter(),
            label = stringResource(id = R.string.recents_lock_unlock),
            description = stringResource(id = R.string.recents_lock_unlock_description),
        ),
        RecentsQuickAction(
            id = 4,
            adapter = prefs.recentsActionClearAll.getAdapter(),
            label = stringResource(id = R.string.recents_clear_all),
        ),
    )

    PreferenceLayout(
        label = stringResource(id = R.string.quickstep_label),
        modifier = modifier,
    ) {
        RestrictedSettingsBanner()
        // The interception feature and the rest of this screen are mutually exclusive by design:
        // interception exists to work around a broken vendor Recents renderer on devices where
        // BlownChart *isn't* the active provider, while every other Quickstep setting below
        // (translucent background, quick actions, corner radius, taskbar) only has any visible
        // effect when BlownChart itself *is* the one actually rendering Recents. Whichever side
        // doesn't apply gets an explanatory warning instead of silently doing nothing.
        if (!BlownChartApp.isRecentsEnabled) {
            PreferenceGroup(
                heading = stringResource(id = R.string.recents_interception_label),
                description = stringResource(id = R.string.recents_button_interception_description),
            ) {
                val recentsButtonInterception = prefs2.recentsButtonInterception.getAdapter()
                SwitchPreference(
                    adapter = recentsButtonInterception,
                    label = stringResource(id = R.string.recents_button_interception_label),
                )
                // Unlike the gesture path, this toggle alone doesn't prompt to turn on
                // Accessibility - nothing runs to show that prompt until the service is already
                // live. Surface it here instead so turning the switch on without the service
                // enabled isn't silently a no-op. Re-checked on every lifecycle change (e.g.
                // returning from the accessibility settings screen).
                val accessibilityServiceBound = remember(lifecycleState()) {
                    context.blownChartApp.isAccessibilityServiceBound()
                }
                ExpandAndShrink(visible = recentsButtonInterception.state.value && !accessibilityServiceBound) {
                    RecentsButtonInterceptionA11yBanner()
                }
                // "Usage access" is a special app-op grant, not a normal manifest permission - it
                // can't be requested via a standard permission dialog, let alone pre-granted from
                // the manifest, only pointed at from here. Without it, the button/gesture silently
                // falls back to firing Recents directly from the launcher's own context again (the
                // original, occasionally-misbehaving behavior this whole feature works around), so
                // surface the gap the same way as the accessibility-service one above rather than
                // leave it silent.
                val usageAccessGranted = remember(lifecycleState()) {
                    context.hasUsageStatsAccess()
                }
                ExpandAndShrink(visible = recentsButtonInterception.state.value && !usageAccessGranted) {
                    RecentsButtonInterceptionUsageAccessBanner()
                }
                // Device admin isn't required for this feature to function, but OEMs are more
                // likely to leave a device-admin app's background process/service alone during
                // aggressive memory/battery cleanup, which is exactly what would otherwise kill
                // the accessibility service this all depends on.
                val deviceAdminActive = remember(lifecycleState()) {
                    context.isDeviceAdminActive()
                }
                ExpandAndShrink(visible = recentsButtonInterception.state.value && !deviceAdminActive) {
                    RecentsButtonInterceptionDeviceAdminBanner()
                }
            }
            QuickstepGeneralSettingsIgnoredWarning()
        } else {
            RecentsInterceptionNotApplicableWarning()
        }
        PreferenceGroup(heading = stringResource(id = R.string.general_label)) {
            SwitchPreference(
                adapter = prefs.recentsTranslucentBackground.getAdapter(),
                label = stringResource(id = R.string.translucent_background),
            )
            val recentsTranslucentBackground by prefs.recentsTranslucentBackground.observeAsState()
            ExpandAndShrink(visible = recentsTranslucentBackground) {
                SliderPreference(
                    adapter = prefs.recentsTranslucentBackgroundAlpha.getAdapter(),
                    label = stringResource(id = R.string.translucent_background_alpha),
                    step = 0.05f,
                    valueRange = 0f..0.95f,
                    showAsPercentage = true,
                )
            }
        }

        QuickActionsPreferences(
            items = recentActionsPreferences,
            adapter = prefs.recentActionOrder.getAdapter(),
        )

        val overrideWindowCornerRadius by prefs.overrideWindowCornerRadius.observeAsState()
        PreferenceGroup(
            heading = stringResource(id = R.string.window_corner_radius_label),
            description = stringResource(id = (R.string.window_corner_radius_description)),
            showDescription = overrideWindowCornerRadius,
        ) {
            SwitchPreference(
                adapter = prefs.overrideWindowCornerRadius.getAdapter(),
                label = stringResource(id = R.string.override_window_corner_radius_label),
            )
            ExpandAndShrink(visible = overrideWindowCornerRadius) {
                SliderPreference(
                    label = stringResource(id = R.string.window_corner_radius_label),
                    adapter = prefs.windowCornerRadius.getAdapter(),
                    step = 0,
                    valueRange = 70..150,
                )
            }
        }

        if (Utilities.ATLEAST_S_V2) {
            PreferenceGroup(
                heading = stringResource(id = R.string.taskbar_label),
            ) {
                SwitchPreference(
                    adapter = prefs2.enableTaskbarOnPhone.getAdapter(),
                    label = stringResource(id = R.string.enable_taskbar_experimental),
                )
            }
        }
    }
}

@PreviewBlownChart
@Composable
private fun QuickstepGeneralSettingsIgnoredWarning(
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        WarningPreference(
            text = stringResource(id = R.string.quickstep_general_settings_ignored_warning),
        )
    }
}

@PreviewBlownChart
@Composable
private fun RecentsInterceptionNotApplicableWarning(
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        WarningPreference(
            text = stringResource(id = R.string.recents_interception_not_applicable_warning),
        )
    }
}

@PreviewBlownChart
@Composable
private fun RecentsButtonInterceptionA11yBanner(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Surface(
        modifier = modifier.padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        PreferenceTemplate(
            modifier = Modifier.clickable {
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .let { context.startActivity(it) }
            },
            title = {},
            description = {
                Text(
                    text = stringResource(id = R.string.recents_button_interception_a11y_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            startWidget = {
                Icon(
                    imageVector = Icons.Rounded.TipsAndUpdates,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    contentDescription = null,
                )
            },
        )
    }
}

@PreviewBlownChart
@Composable
private fun RecentsButtonInterceptionUsageAccessBanner(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Surface(
        modifier = modifier.padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        PreferenceTemplate(
            modifier = Modifier.clickable { context.openUsageAccessSettings() },
            title = {},
            description = {
                Text(
                    text = stringResource(id = R.string.recents_button_interception_usage_access_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            startWidget = {
                Icon(
                    imageVector = Icons.Rounded.TipsAndUpdates,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    contentDescription = null,
                )
            },
        )
    }
}

@PreviewBlownChart
@Composable
private fun RecentsButtonInterceptionDeviceAdminBanner(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Surface(
        modifier = modifier.padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        PreferenceTemplate(
            modifier = Modifier.clickable { context.requestDeviceAdmin() },
            title = {},
            description = {
                Text(
                    text = stringResource(id = R.string.recents_button_interception_device_admin_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            startWidget = {
                Icon(
                    imageVector = Icons.Rounded.TipsAndUpdates,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    contentDescription = null,
                )
            },
        )
    }
}
