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

package app.lawnchair.security

import android.content.Context
import app.lawnchair.preferences2.PreferenceManager2
import com.patrykmichalik.opto.core.firstBlocking
import com.patrykmichalik.opto.core.setBlocking

/**
 * Whether Lawnchair's own settings and any exit into system Settings require the settings-lock
 * PIN (or biometric) to proceed, plus the PIN storage/validation logic backing that gate. See
 * [SettingsLockUnlockActivity] for the actual unlock UI.
 */
object SettingsLockGate {
    const val MIN_PIN_LENGTH = 4
    const val MAX_PIN_LENGTH = 16

    fun isEnabled(context: Context): Boolean {
        val prefs2 = PreferenceManager2.getInstance(context)
        return prefs2.settingsLockEnabled.firstBlocking() && prefs2.settingsLockPinHash.firstBlocking().isNotEmpty()
    }

    fun isBiometricOfferEnabled(context: Context): Boolean = PreferenceManager2.getInstance(context).settingsLockBiometricEnabled.firstBlocking()

    fun isValidPin(pin: String): Boolean = pin.length in MIN_PIN_LENGTH..MAX_PIN_LENGTH

    fun verifyPin(context: Context, pin: String): Boolean {
        val storedHash = PreferenceManager2.getInstance(context).settingsLockPinHash.firstBlocking()
        if (storedHash.isEmpty()) return false
        return PinHasher.verify(pin, storedHash)
    }

    /** Sets a new PIN and enables the lock. [pin] must already satisfy [isValidPin]. */
    fun setPin(context: Context, pin: String) {
        val prefs2 = PreferenceManager2.getInstance(context)
        prefs2.settingsLockPinHash.setBlocking(PinHasher.hash(pin))
        prefs2.settingsLockEnabled.setBlocking(true)
    }

    /** Disables the lock and forgets the stored PIN hash entirely. */
    fun disable(context: Context) {
        val prefs2 = PreferenceManager2.getInstance(context)
        prefs2.settingsLockEnabled.setBlocking(false)
        prefs2.settingsLockPinHash.setBlocking("")
    }
}
