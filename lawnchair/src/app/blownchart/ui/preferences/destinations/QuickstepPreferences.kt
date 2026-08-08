package app.blownchart.ui.preferences.destinations

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.blownchart.BlownChartApp
import app.blownchart.preferences.getAdapter
import app.blownchart.preferences.observeAsState
import app.blownchart.preferences.preferenceManager
import app.blownchart.preferences2.preferenceManager2
import app.blownchart.ui.preferences.components.QuickActionsPreferences
import app.blownchart.ui.preferences.components.RecentsQuickAction
import app.blownchart.ui.preferences.components.controls.SliderPreference
import app.blownchart.ui.preferences.components.controls.SwitchPreference
import app.blownchart.ui.preferences.components.controls.WarningPreference
import app.blownchart.ui.preferences.components.layout.ExpandAndShrink
import app.blownchart.ui.preferences.components.layout.PreferenceGroup
import app.blownchart.ui.preferences.components.layout.PreferenceLayout
import app.blownchart.ui.util.preview.PreviewBlownChart
import app.blownchart.util.isOnePlusStock
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
        if (!BlownChartApp.isRecentsEnabled) {
            QuickSwitchIgnoredWarning()
            PreferenceGroup(
                heading = stringResource(id = R.string.recents_interception_label),
                description = stringResource(id = R.string.recents_button_interception_description),
            ) {
                SwitchPreference(
                    adapter = prefs2.recentsButtonInterception.getAdapter(),
                    label = stringResource(id = R.string.recents_button_interception_label),
                )
            }
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
private fun QuickSwitchIgnoredWarning(
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        WarningPreference(
            text = stringResource(id = R.string.quickswitch_ignored_warning),
        )
    }
}
