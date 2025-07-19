package app.lawnchair.ui.preferences.destinations

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.datastore.preferences.core.Preferences
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.controls.MainSwitchPreference
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.controls.TextPreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.data.liveinfo.liveInformationManager
import app.lawnchair.ui.preferences.data.liveinfo.model.LiveInformation
import app.lawnchair.ui.preferences.navigation.FeatureFlags
import com.patrykmichalik.opto.domain.Preference
import kotlinx.coroutines.runBlocking

/**
 * A screen to house unfinished preferences and debug flags
 */
@Composable
fun DebugMenuPreferences(
    modifier: Modifier = Modifier,
) {
    val prefs = preferenceManager()
    val prefs2 = preferenceManager2()
    val liveInfoManager = liveInformationManager()
    val flags = remember { prefs.debugFlags }
    val flags2 = remember { prefs2.debugFlags }
    val textFlags = remember { prefs2.textFlags }
    val navController = LocalNavController.current

    val enableDebug = prefs.enableDebugMenu.getAdapter()

    PreferenceLayout(
        label = "Debug menu",
        backArrowVisible = !LocalIsExpandedScreen.current,
        modifier = modifier,
    ) {
        MainSwitchPreference(adapter = enableDebug, label = "Show debug menu") {
            PreferenceGroup {
                ClickablePreference(
                    label = "Feature flags",
                    onClick = {
                        navController.navigate(FeatureFlags)
                    },
                )
                ClickablePreference(
                    label = "Crash launcher",
                    onClick = { throw RuntimeException("User triggered crash") },
                )
                ClickablePreference(
                    label = "Reset live information",
                    onClick = {
                        runBlocking {
                            liveInfoManager.liveInformation.set(LiveInformation())
                            liveInfoManager.dismissedAnnouncementIds.set(emptySet())
                        }
                    },
                )
            }

            PreferenceGroup(heading = "Debug flags") {
                flags2.forEach {
                    SwitchPreference(
                        adapter = it.getAdapter(),
                        label = it.key.name,
                    )
                }
                flags.forEach {
                    SwitchPreference(
                        adapter = it.getAdapter(),
                        label = it.key,
                    )
                }
                textFlags.forEach {
                    TextPreference(
                        adapter = it.getAdapter(),
                        label = it.key.name,
                    )
                }
            }
        }
    }
}

private val PreferenceManager2.debugFlags: List<Preference<Boolean, Boolean, Preferences.Key<Boolean>>>
    get() = listOf(showComponentNames, legacyPopupOptionsMigrated)

private val PreferenceManager2.textFlags: List<Preference<String, String, Preferences.Key<String>>>
    get() = listOf(additionalFonts, launcherPopupOrder)

private val PreferenceManager.debugFlags
    get() = listOf(ignoreFeedWhitelist)
