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

package app.blownchart.ui.preferences.about

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.blownchart.ui.preferences.LocalIsExpandedScreen
import app.blownchart.ui.preferences.components.NavigationActionPreference
import app.blownchart.ui.preferences.components.controls.ClickablePreference
import app.blownchart.ui.preferences.components.layout.PreferenceDivider
import app.blownchart.ui.preferences.components.layout.PreferenceGroupHeading
import app.blownchart.ui.preferences.components.layout.PreferenceGroupItem
import app.blownchart.ui.preferences.components.layout.PreferenceLayoutLazyColumn
import app.blownchart.ui.preferences.navigation.AboutDonate
import app.blownchart.ui.preferences.navigation.AboutLicenses
import com.android.launcher3.BuildConfig
import com.android.launcher3.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun About(
    modifier: Modifier = Modifier,
    viewModel: AboutViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val sheetState = rememberModalBottomSheetState(true)
    var openBottomSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (openBottomSheet) {
        val updateState = uiState.updateState
        if (updateState is UpdateState.Available) {
            ChangesDialog(
                changelogState = updateState.changelogState,
                onDismiss = {
                    scope.launch {
                        sheetState.hide()
                    }.invokeOnCompletion {
                        openBottomSheet = false
                    }
                },
                onDownload = {
                    viewModel.downloadUpdate()
                },
                sheetState = sheetState,
            )
        }
    }

    PreferenceLayoutLazyColumn(
        label = stringResource(id = R.string.about_label),
        modifier = modifier,
        backArrowVisible = !LocalIsExpandedScreen.current,
    ) {
        item {
            Spacer(Modifier.padding(top = 8.dp))
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_home_comp),
                    contentDescription = null,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape),
                )
            }
        }
        item {
            Spacer(modifier = Modifier.height(12.dp))
        }
        item {
            Text(
                text = stringResource(id = R.string.derived_app_name),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = BuildConfig.VERSION_DISPLAY_NAME,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
        item {
            UpdateSection(
                updateState = uiState.updateState,
                onInstall = {
                    viewModel.installUpdate(it)
                },
                onViewChanges = {
                    openBottomSheet = true
                    scope.launch {
                        sheetState.show()
                    }
                },
            )
        }
        item {
            Spacer(modifier = Modifier.requiredHeight(16.dp))
        }
        item {
            Text(
                text = stringResource(id = R.string.about_developed_by, "Wolk Tambowskij"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
        item {
            Text(
                text = stringResource(id = R.string.about_fork_disclosure),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
        }
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                uiState.topLinks.forEach { link ->
                    BlownChartLink(
                        iconResId = link.iconResId,
                        label = stringResource(id = link.labelResId),
                        modifier = Modifier.weight(weight = 1f),
                        url = link.url,
                    )
                }
            }
        }
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
        item {
            PreferenceGroupHeading(
                stringResource(R.string.about_whats_new_heading),
            )
        }
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                listOf(
                    R.string.about_change_1,
                    R.string.about_change_2,
                    R.string.about_change_3,
                    R.string.about_change_4,
                    R.string.about_change_5,
                    R.string.about_change_6,
                    R.string.about_change_7,
                    R.string.about_change_8,
                    R.string.about_change_9,
                    R.string.about_change_10,
                    R.string.about_change_11,
                    R.string.about_change_12,
                    R.string.about_change_13,
                    R.string.about_change_14,
                    R.string.about_change_15,
                    R.string.about_change_16,
                    R.string.about_change_17,
                    R.string.about_change_18,
                    R.string.about_change_19,
                ).forEach { changeRes ->
                    Row(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(text = "• ", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = stringResource(id = changeRes),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
        item {
            PreferenceGroupHeading(
                stringResource(R.string.about_recommendations_heading),
            )
        }
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Text(
                    text = stringResource(id = R.string.about_recommendation_device_admin),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(id = R.string.about_recommendation_autostart),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
        item {
            PreferenceGroupHeading(
                stringResource(R.string.donate_label),
            )
        }
        item {
            PreferenceGroupItem(
                cutTop = false,
                cutBottom = false,
            ) {
                NavigationActionPreference(
                    label = stringResource(id = R.string.donate_label),
                    subtitle = stringResource(id = R.string.donate_nav_subtitle),
                    destination = AboutDonate,
                )
            }
        }
        item {
            PreferenceGroupHeading(
                stringResource(R.string.legal),
            )
        }
        item {
            PreferenceGroupItem(
                cutTop = false,
                cutBottom = true,
            ) {
                NavigationActionPreference(
                    label = stringResource(id = R.string.acknowledgements),
                    destination = AboutLicenses,
                )
            }
        }
        item {
            PreferenceGroupItem(
                cutTop = true,
                cutBottom = true,
            ) {
                // PreferenceGroupItem's content sits directly in a Surface/Box, so multiple
                // real (non-divider) children need an explicit Column or they'd overlap
                // instead of stacking.
                Column {
                    PreferenceDivider()
                    Text(
                        text = stringResource(id = R.string.about_privacy_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                    ClickablePreference(
                        label = stringResource(id = R.string.privacy_policy),
                        onClick = {
                            val webpage = PRIVACY_POLICY.toUri()
                            val intent = Intent(Intent.ACTION_VIEW, webpage)
                            if (intent.resolveActivity(context.packageManager) != null) {
                                context.startActivity(intent)
                            }
                        },
                    )
                }
            }
        }
    }
}

private const val PRIVACY_POLICY = "https://lawnchair.app/privacy_policy"
