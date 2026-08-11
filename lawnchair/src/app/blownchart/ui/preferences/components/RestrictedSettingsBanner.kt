package app.blownchart.ui.preferences.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.blownchart.ui.preferences.components.controls.WarningPreference
import app.blownchart.ui.preferences.destinations.openAppInfo
import app.blownchart.ui.util.preview.PreviewBlownChart
import com.android.launcher3.R

/**
 * Unconditional hint about Android's "Restricted settings" sideloading protection: for any APK
 * installed outside an app store (including a direct download from GitHub Releases), the OS
 * blocks navigation to sensitive settings screens - notably Accessibility - until the user
 * manually visits App info and taps "Allow restricted settings" from the overflow menu. There's
 * no API to query whether the restriction is currently active, so this can only ever be an
 * always-shown informational tap target rather than something conditionally gated on state.
 */
@PreviewBlownChart
@Composable
fun RestrictedSettingsBanner(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Surface(
        modifier = modifier.padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        WarningPreference(
            modifier = Modifier.clickable { openAppInfo(context) },
            text = stringResource(id = R.string.restricted_settings_hint),
        )
    }
}
