package app.blownchart.ui.preferences.components.controls

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.blownchart.ui.preferences.components.layout.PreferenceTemplate
import app.blownchart.ui.theme.BlownChartTheme
import app.blownchart.ui.util.preview.PreferenceGroupPreviewContainer
import app.blownchart.ui.util.preview.PreviewBlownChart

@Composable
fun WarningPreference(
    text: String,
    modifier: Modifier = Modifier,
) {
    PreferenceTemplate(
        modifier = modifier,
        title = {},
        description = {
            Text(
                text = text,
                color = MaterialTheme.colorScheme.error,
            )
        },
        startWidget = {
            Icon(
                imageVector = Icons.Rounded.Warning,
                tint = MaterialTheme.colorScheme.error,
                contentDescription = null,
            )
        },
    )
}

@PreviewBlownChart
@Composable
private fun WarningPreferencePreview() {
    BlownChartTheme {
        PreferenceGroupPreviewContainer {
            WarningPreference(
                text = "Text",
            )
        }
    }
}
