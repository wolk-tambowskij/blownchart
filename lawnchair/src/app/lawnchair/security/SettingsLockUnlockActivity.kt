package app.lawnchair.security

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import app.lawnchair.ui.theme.LawnchairTheme
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
            LawnchairTheme {
                SettingsLockUnlockScreen(
                    startInCreatePinMode = isSetupMode,
                    canUseBiometric = canUseBiometric(),
                    onUnlockWithPin = { pin -> SettingsLockGate.verifyPin(this, pin) },
                    onCreatePin = { pin -> SettingsLockGate.setPin(this, pin) },
                    onRequestBiometric = { onSuccess -> showBiometricPrompt(onSuccess) },
                    onUnlocked = {
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

    companion object {
        private const val EXTRA_SETUP_MODE = "app.lawnchair.security.EXTRA_SETUP_MODE"

        /** Unlock an already-configured PIN before proceeding to something gated. */
        fun createUnlockIntent(context: Context): Intent =
            Intent(context, SettingsLockUnlockActivity::class.java)

        /** First-time (or "forgot PIN") PIN creation; succeeds by setting a brand new PIN. */
        fun createSetupIntent(context: Context): Intent =
            Intent(context, SettingsLockUnlockActivity::class.java)
                .putExtra(EXTRA_SETUP_MODE, true)
    }
}
