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

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import app.lawnchair.ui.theme.BlownChartTheme
import com.android.launcher3.R

/**
 * Hosts the settings-lock PIN/biometric prompt. Kept as a small, dedicated [FragmentActivity]
 * ([BiometricPrompt] requires a FragmentActivity/Fragment host) rather than changing the base
 * Activity classes used throughout the launcher. Callers launch it via an `ActivityResultLauncher`
 * ([createUnlockIntent] or [createSetupIntent]) and treat `RESULT_OK` as unlocked, anything else
 * as not unlocked.
 */
class SettingsLockUnlockActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (!SettingsLockGate.isEnabled(this) && !isSetupMode) {
            // Nothing to unlock; don't block the caller.
            setResult(RESULT_OK)
            finish()
            return
        }

        setContent {
            BlownChartTheme {
                SettingsLockUnlockScreen(
                    startInCreatePinMode = isSetupMode,
                    canUseBiometric = canUseBiometric(),
                    onUnlockWithPin = { pin -> SettingsLockGate.verifyPin(this, pin) },
                    onCreatePin = { pin -> SettingsLockGate.setPin(this, pin) },
                    onRequestBiometric = { onSuccess -> showBiometricPrompt(onSuccess) },
                    onUnlock = {
                        launchIntentAfterUnlock?.let { startIntentSafely(it) }
                        setResult(RESULT_OK)
                        finish()
                    },
                    onCancel = {
                        setResult(RESULT_CANCELED)
                        finish()
                    },
                )
            }
        }
    }

    private fun canUseBiometric(): Boolean {
        if (!SettingsLockGate.isBiometricOfferEnabled(this)) return false
        return BiometricManager.from(this).canAuthenticate(BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun showBiometricPrompt(onSuccess: () -> Unit) {
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.settings_lock_biometric_prompt_title))
            .setNegativeButtonText(getString(android.R.string.cancel))
            .build()
        BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }
            },
        ).authenticate(promptInfo)
    }

    private val isSetupMode: Boolean
        get() = intent?.getBooleanExtra(EXTRA_SETUP_MODE, false) == true

    // Launched directly by this Activity (which is already in the foreground with focus) right
    // before it finishes, instead of having the caller launch it from an ActivityResultCallback
    // after this Activity returns control - avoids a launch-after-round-trip that turned out to
    // silently no-op for a plain home/drawer/dock icon tap.
    private val launchIntentAfterUnlock: Intent?
        get() = intent?.getParcelableExtra(EXTRA_LAUNCH_INTENT)

    companion object {
        private const val EXTRA_SETUP_MODE = "app.lawnchair.security.EXTRA_SETUP_MODE"
        private const val EXTRA_LAUNCH_INTENT = "app.lawnchair.security.EXTRA_LAUNCH_INTENT"

        /**
         * Unlock an already-configured PIN before proceeding to something gated. If
         * [launchIntentAfterUnlock] is given, this Activity starts it directly on success instead
         * of relying on the caller to do so from an `ActivityResultCallback`.
         */
        fun createUnlockIntent(context: Context, launchIntentAfterUnlock: Intent? = null): Intent = Intent(context, SettingsLockUnlockActivity::class.java).apply {
            if (launchIntentAfterUnlock != null) {
                putExtra(EXTRA_LAUNCH_INTENT, launchIntentAfterUnlock)
            }
        }

        /** First-time (or "forgot PIN") PIN creation; succeeds by setting a brand new PIN. */
        fun createSetupIntent(context: Context): Intent = Intent(context, SettingsLockUnlockActivity::class.java)
            .putExtra(EXTRA_SETUP_MODE, true)
    }
}

/** Launches [intent], showing a toast instead of crashing if nothing can handle it. */
fun Context.startIntentSafely(intent: Intent) {
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, R.string.activity_not_found, Toast.LENGTH_SHORT).show()
    }
}
