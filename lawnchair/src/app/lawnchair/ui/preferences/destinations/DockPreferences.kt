/*
 * Copyright 2022, Lawnchair
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

package app.lawnchair.ui.preferences.destinations

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.lawnchair.hotseat.HotseatMode
import app.lawnchair.hotseat.LawnchairHotseat
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.qsb.providers.QsbSearchProvider
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.DummyLauncherBox
import app.lawnchair.ui.preferences.components.DummyLauncherLayout
import app.lawnchair.ui.preferences.components.NavigationActionPreference
import app.lawnchair.ui.preferences.components.WallpaperPreview
import app.lawnchair.ui.preferences.components.clipToPercentage
import app.lawnchair.ui.preferences.components.clipToVisiblePercentage
import app.lawnchair.ui.preferences.components.colorpreference.ColorPreference
import app.lawnchair.ui.preferences.components.controls.ListPreference
import app.lawnchair.ui.preferences.components.controls.ListPreferenceEntry
import app.lawnchair.ui.preferences.components.controls.MainSwitchPreference
import app.lawnchair.ui.preferences.components.controls.SliderPreference
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.createPreviewIdp
import app.lawnchair.ui.preferences.components.layout.DividerColumn
import app.lawnchair.ui.preferences.components.layout.ExpandAndShrink
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import com.android.launcher3.R

object DockRoutes {
    const val SEARCH_PROVIDER = "searchProvider"
}

@Composable
fun DockPreferences(
    modifier: Modifier = Modifier,
) {
    val prefs = preferenceManager()
    val prefs2 = preferenceManager2()
    val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT

    PreferenceLayout(
        label = stringResource(id = R.string.dock_label),
        backArrowVisible = !LocalIsExpandedScreen.current,
        modifier = modifier,
    ) {
        val isHotseatEnabled = prefs2.isHotseatEnabled.getAdapter()
        val hotseatModeAdapter = prefs2.hotseatMode.getAdapter()
        val hotseatColumnAdapter = prefs.hotseatColumns.getAdapter()
        val themeQsbAdapter = prefs2.themedHotseatQsb.getAdapter()
        val qsbCornerAdapter = prefs.hotseatQsbCornerRadius.getAdapter()
        val qsbAlphaAdapter = prefs.hotseatQsbAlpha.getAdapter()
        val qsbHotseatStrokeWidth = prefs.hotseatQsbStrokeWidth.getAdapter()
        val hotseatBottomFactorAdapter = prefs2.hotseatBottomFactor.getAdapter()
        val strokeColorStyleAdapter = prefs2.strokeColorStyle.getAdapter()
        val hotseatBgAdapter = prefs.hotseatBG.getAdapter()
        val hotseatBgHorizontalInsetLeftAdapter = prefs.hotseatBGHorizontalInsetLeft.getAdapter()
        val hotseatBgVerticalInsetTopAdapter = prefs.hotseatBGVerticalInsetTop.getAdapter()
        val hotseatBgHorizontalInsetRightAdapter = prefs.hotseatBGHorizontalInsetRight.getAdapter()
        val hotseatBgVerticalInsetBottomAdapter = prefs.hotseatBGVerticalInsetBottom.getAdapter()

        MainSwitchPreference(adapter = isHotseatEnabled, label = stringResource(id = R.string.show_hotseat_title)) {
            if (isPortrait) {
                PreferenceGroup(
                    heading = stringResource(id = R.string.preview_label),
                ) {
                    DummyLauncherBox(
                        modifier = Modifier.weight(1f)
                            .align(Alignment.CenterHorizontally)
                            .clip(MaterialTheme.shapes.large)
                            .clipToVisiblePercentage(0.3f)
                            .clipToPercentage(1.0f),
                    ) {
                        WallpaperPreview(modifier = Modifier.fillMaxSize())
                        key(
                            isHotseatEnabled.state.value,
                            hotseatModeAdapter.state.value,
                            hotseatColumnAdapter.state.value,
                            themeQsbAdapter.state.value,
                            qsbCornerAdapter.state.value,
                            qsbAlphaAdapter.state.value,
                            qsbHotseatStrokeWidth.state.value,
                            strokeColorStyleAdapter.state.value,
                            hotseatBgAdapter.state.value,
                            hotseatBgHorizontalInsetLeftAdapter.state.value,
                            hotseatBgVerticalInsetTopAdapter.state.value,
                            hotseatBgHorizontalInsetRightAdapter.state.value,
                            hotseatBgVerticalInsetBottomAdapter.state.value,
                            hotseatBottomFactorAdapter.state.value,
                        ) {
                            DummyLauncherLayout(
                                idp = createPreviewIdp { copy(numHotseatColumns = prefs.hotseatColumns.get()) },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }

            PreferenceGroup(heading = stringResource(id = R.string.general_label)) {
                SwitchPreference(
                    adapter = hotseatBgAdapter,
                    label = stringResource(id = R.string.hotseat_background),
                )
                ExpandAndShrink(visible = hotseatBgAdapter.state.value) {
                    DividerColumn {
                        SliderPreference(
                            label = stringResource(id = R.string.hotseat_bg_horizontal_inset_left),
                            adapter = hotseatBgHorizontalInsetLeftAdapter,
                            step = 1,
                            valueRange = 0..100,
                            showUnit = "px",
                        )
                        SliderPreference(
                            label = stringResource(id = R.string.hotseat_bg_horizontal_inset_right),
                            adapter = hotseatBgHorizontalInsetRightAdapter,
                            step = 1,
                            valueRange = 0..100,
                            showUnit = "px",
                        )
                        SliderPreference(
                            label = stringResource(id = R.string.hotseat_bg_vertical_inset_top),
                            adapter = hotseatBgVerticalInsetTopAdapter,
                            step = 1,
                            valueRange = 0..100,
                            showUnit = "px",
                        )
                        SliderPreference(
                            label = stringResource(id = R.string.hotseat_bg_vertical_inset_bottom),
                            adapter = hotseatBgVerticalInsetBottomAdapter,
                            step = 1,
                            valueRange = 0..100,
                            showUnit = "px",
                        )
                    }
                }
            }

            PreferenceGroup(heading = stringResource(id = R.string.search_bar_label)) {
                HotseatModePreference(
                    adapter = hotseatModeAdapter,
                )
                ExpandAndShrink(visible = hotseatModeAdapter.state.value == LawnchairHotseat) {
                    DividerColumn {
                        val hotseatQsbProviderAdapter by preferenceManager2().hotseatQsbProvider.getAdapter()
                        NavigationActionPreference(
                            label = stringResource(R.string.search_provider),
                            destination = DockRoutes.SEARCH_PROVIDER,
                            subtitle = stringResource(
                                id = QsbSearchProvider.values()
                                    .first { it == hotseatQsbProviderAdapter }
                                    .name,
                            ),
                        )
                        SwitchPreference(
                            adapter = themeQsbAdapter,
                            label = stringResource(id = R.string.apply_accent_color_label),
                        )
                        SliderPreference(
                            label = stringResource(id = R.string.corner_radius_label),
                            adapter = qsbCornerAdapter,
                            step = 0.05F,
                            valueRange = 0F..1F,
                            showAsPercentage = true,
                        )
                        SliderPreference(
                            label = stringResource(id = R.string.qsb_hotseat_background_transparency),
                            adapter = qsbAlphaAdapter,
                            step = 5,
                            valueRange = 0..100,
                            showUnit = "%",
                        )

                        SliderPreference(
                            label = stringResource(id = R.string.qsb_hotseat_stroke_width),
                            adapter = qsbHotseatStrokeWidth,
                            step = 1f,
                            valueRange = 0f..10f,
                            showUnit = "vw",
                        )
                        ExpandAndShrink(visible = qsbHotseatStrokeWidth.state.value > 0f) {
                            ColorPreference(preference = prefs2.strokeColorStyle)
                        }
                    }
                }
            }
            PreferenceGroup(heading = stringResource(id = R.string.grid)) {
                SliderPreference(
                    label = stringResource(id = R.string.dock_icons),
                    adapter = hotseatColumnAdapter,
                    step = 1,
                    valueRange = 3..10,
                )
                SliderPreference(
                    adapter = hotseatBottomFactorAdapter,
                    label = stringResource(id = R.string.hotseat_bottom_space_label),
                    valueRange = 0.0F..1.7F,
                    step = 0.1F,
                    showAsPercentage = true,
                )
            }
        }
    }
}

@Composable
private fun HotseatModePreference(
    adapter: PreferenceAdapter<HotseatMode>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val entries = remember {
        HotseatMode.values().map { mode ->
            ListPreferenceEntry(
                value = mode,
                label = { stringResource(id = mode.nameResourceId) },
                enabled = mode.isAvailable(context = context),
            )
        }
    }

    ListPreference(
        adapter = adapter,
        entries = entries,
        label = stringResource(id = R.string.hotseat_mode_label),
        modifier = modifier,
    )
}
