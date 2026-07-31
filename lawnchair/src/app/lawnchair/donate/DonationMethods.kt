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

package app.lawnchair.donate

import androidx.annotation.StringRes
import com.android.launcher3.R

/**
 * How a [DonationMethod] is presented to the user. No method ever unlocks or gates any
 * launcher functionality - see docs/DONATIONS.md.
 */
enum class DonationMethodType {
    /** Opens [DonationMethod.value] (a URL) in a browser via ACTION_VIEW. */
    LINK,

    /** Shows a locally-generated QR code encoding [DonationMethod.value]. */
    QR,

    /** Copies [DonationMethod.value] to the clipboard. */
    COPY_TEXT,
}

data class DonationMethod(
    val id: String,
    @StringRes val titleRes: Int,
    val type: DonationMethodType,
    val value: String,
    /**
     * False for methods still carrying a placeholder [value]. Filter these out of any UI
     * until the real value is filled in - see docs/DONATIONS.md.
     */
    val enabled: Boolean = true,
)

/**
 * Single source of truth for donation/support channels. Referenced by the "Support
 * development" preference screen; never by anything that changes app behavior.
 */
object DonationMethods {

    val all: List<DonationMethod> = listOf(
        DonationMethod(
            id = "paypal",
            titleRes = R.string.donate_method_paypal,
            type = DonationMethodType.LINK,
            value = "https://www.paypal.com/donate/?business=wolk.tambowskij%40gmail.com&currency_code=USD",
        ),
        DonationMethod(
            id = "yoomoney",
            titleRes = R.string.donate_method_yoomoney,
            type = DonationMethodType.LINK,
            value = "https://yoomoney.ru/to/4100119588109985",
        ),
        DonationMethod(
            id = "boosty",
            titleRes = R.string.donate_method_boosty,
            type = DonationMethodType.LINK,
            value = "https://boosty.to/<PLACEHOLDER>",
            enabled = false,
        ),
        DonationMethod(
            id = "cloudtips",
            titleRes = R.string.donate_method_cloudtips,
            type = DonationMethodType.LINK,
            value = "https://pay.cloudtips.ru/p/<PLACEHOLDER>",
            enabled = false,
        ),
        DonationMethod(
            id = "mir_card",
            titleRes = R.string.donate_method_mir_card,
            type = DonationMethodType.COPY_TEXT,
            value = "<PLACEHOLDER_CARD_NUMBER>",
            enabled = false,
        ),
        DonationMethod(
            id = "crypto",
            titleRes = R.string.donate_method_crypto,
            type = DonationMethodType.COPY_TEXT,
            value = "<PLACEHOLDER_CRYPTO_ADDRESS>",
            enabled = false,
        ),
    )

    /** Methods ready to show to users - excludes anything still carrying a placeholder value. */
    val enabledMethods: List<DonationMethod> get() = all.filter { it.enabled }
}
