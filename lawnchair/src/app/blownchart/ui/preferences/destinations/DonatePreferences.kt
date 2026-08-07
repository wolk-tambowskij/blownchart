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

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.QrCode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.blownchart.donate.DonationMethod
import app.blownchart.donate.DonationMethodType
import app.blownchart.donate.DonationMethods
import app.blownchart.donate.copyDonationText
import app.blownchart.donate.generateDonationQrBitmap
import app.blownchart.donate.openDonationLink
import app.blownchart.ui.preferences.components.layout.PreferenceGroup
import app.blownchart.ui.preferences.components.layout.PreferenceLayout
import app.blownchart.ui.preferences.components.layout.PreferenceTemplate
import com.android.launcher3.BuildConfig
import com.android.launcher3.R

@Composable
fun DonatePreferences(modifier: Modifier = Modifier) {
    PreferenceLayout(
        label = stringResource(id = R.string.donate_label),
        backArrowVisible = true,
        modifier = modifier,
    ) {
        Text(
            text = stringResource(id = R.string.donate_thanks_message),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        if (BuildConfig.DONATIONS_SHOW_PAYMENT_LINKS) {
            PreferenceGroup {
                DonationMethods.all.forEach { method ->
                    DonationMethodRow(method)
                }
            }
        } else {
            // Google Play build: no in-app payment links, per Play Billing policy.
            // See docs/DONATIONS.md.
            PreferenceGroup {
                DonateLinkRow(
                    titleRes = R.string.donate_play_fallback_title,
                    descriptionRes = R.string.donate_play_fallback_description,
                    url = "https://github.com/wolk-tambowskij/blownchart",
                )
            }
        }
    }
}

@Composable
private fun DonationMethodRow(method: DonationMethod) {
    val context = LocalContext.current
    var showQrDialog by remember { mutableStateOf(false) }

    if (showQrDialog) {
        DonationQrDialog(
            value = method.value,
            onDismiss = { showQrDialog = false },
        )
    }

    val descriptionRes = when (method.type) {
        DonationMethodType.LINK -> R.string.donate_action_open_link
        DonationMethodType.COPY_TEXT -> R.string.donate_action_copy
        DonationMethodType.QR -> R.string.donate_action_show_qr
    }
    val icon = when (method.type) {
        DonationMethodType.LINK -> Icons.Rounded.Link
        DonationMethodType.COPY_TEXT -> Icons.Rounded.ContentCopy
        DonationMethodType.QR -> Icons.Rounded.QrCode
    }
    PreferenceTemplate(
        title = { Text(text = stringResource(id = method.titleRes)) },
        description = { Text(text = stringResource(id = descriptionRes)) },
        startWidget = {
            Icon(imageVector = icon, contentDescription = null)
        },
        modifier = Modifier.clickable {
            when (method.type) {
                DonationMethodType.LINK -> openDonationLink(context, method.value)
                DonationMethodType.COPY_TEXT -> copyDonationText(context, method.value)
                DonationMethodType.QR -> showQrDialog = true
            }
        },
    )
}

@Composable
private fun DonateLinkRow(
    titleRes: Int,
    descriptionRes: Int,
    url: String,
) {
    val context = LocalContext.current
    PreferenceTemplate(
        title = { Text(text = stringResource(id = titleRes)) },
        description = { Text(text = stringResource(id = descriptionRes)) },
        startWidget = {
            Icon(imageVector = Icons.Rounded.Link, contentDescription = null)
        },
        modifier = Modifier.clickable { openDonationLink(context, url) },
    )
}

@Composable
private fun DonationQrDialog(value: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val bitmap = remember(value) { generateDonationQrBitmap(value) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(id = R.string.donate_qr_dialog_title)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(240.dp),
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { copyDonationText(context, value) }) {
                Icon(imageVector = Icons.Rounded.ContentCopy, contentDescription = null)
                Text(text = stringResource(id = R.string.donate_action_copy))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = android.R.string.ok))
            }
        },
    )
}
