package app.blownchart.ui.preferences.components

import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.blownchart.preferences.BasePreferenceManager
import app.blownchart.preferences.getAdapter
import app.blownchart.ui.preferences.LocalNavController
import app.blownchart.ui.preferences.components.layout.PreferenceTemplate
import app.blownchart.ui.preferences.navigation.GeneralFontSelection

@Composable
fun FontPreference(
    fontPref: BasePreferenceManager.FontPref,
    label: String,
    modifier: Modifier = Modifier,
) {
    val navController = LocalNavController.current

    PreferenceTemplate(
        title = { Text(text = label) },
        description = {
            val font = fontPref.getAdapter().state.value
            Text(
                text = font.fullDisplayName,
                fontFamily = font.composeFontFamily,
            )
        },
        modifier = modifier
            .clickable { navController.navigate(route = GeneralFontSelection(fontPref.key)) },
    )
}
