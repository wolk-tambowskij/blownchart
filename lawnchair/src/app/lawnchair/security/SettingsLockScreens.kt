package app.lawnchair.security

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.android.launcher3.R

private enum class LockScreenMode { UNLOCK, CREATE_PIN }

/**
 * Self-contained unlock/create-PIN screen for [SettingsLockUnlockActivity]. Starting in
 * [LockScreenMode.CREATE_PIN] covers first-time PIN setup; starting in [LockScreenMode.UNLOCK]
 * covers the normal gate, and can transition to [LockScreenMode.CREATE_PIN] locally after a
 * successful "forgot PIN" biometric check, without restarting the Activity.
 */
@Composable
fun SettingsLockUnlockScreen(
    startInCreatePinMode: Boolean,
    canUseBiometric: Boolean,
    onUnlockWithPin: (String) -> Boolean,
    onCreatePin: (String) -> Unit,
    onRequestBiometric: (onSuccess: () -> Unit) -> Unit,
    onUnlock: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var mode by remember { mutableStateOf(if (startInCreatePinMode) LockScreenMode.CREATE_PIN else LockScreenMode.UNLOCK) }
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorRes by remember { mutableStateOf<Int?>(null) }

    BackHandler(onBack = onCancel)

    fun submit() {
        when (mode) {
            LockScreenMode.UNLOCK -> {
                if (onUnlockWithPin(pin)) {
                    onUnlock()
                } else {
                    errorRes = R.string.settings_lock_wrong_pin
                    pin = ""
                }
            }

            LockScreenMode.CREATE_PIN -> {
                when {
                    !SettingsLockGate.isValidPin(pin) -> errorRes = R.string.settings_lock_pin_length_error

                    pin != confirmPin -> errorRes = R.string.settings_lock_pin_mismatch

                    else -> {
                        onCreatePin(pin)
                        onUnlock()
                    }
                }
            }
        }
    }

    Scaffold(modifier = modifier.fillMaxSize()) { contentPadding ->
        Column(
            modifier = Modifier
                .padding(contentPadding)
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(
                    if (mode == LockScreenMode.CREATE_PIN) R.string.settings_lock_create_pin_title else R.string.settings_lock_unlock_title,
                ),
                style = MaterialTheme.typography.headlineSmall,
            )
            if (mode == LockScreenMode.CREATE_PIN) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_lock_create_pin_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = pin,
                onValueChange = {
                    pin = it
                    errorRes = null
                },
                label = { Text(stringResource(R.string.settings_lock_pin_label)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                isError = errorRes != null,
                shape = RoundedCornerShape(32.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            if (mode == LockScreenMode.CREATE_PIN) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = {
                        confirmPin = it
                        errorRes = null
                    },
                    label = { Text(stringResource(R.string.settings_lock_confirm_pin_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    isError = errorRes != null,
                    shape = RoundedCornerShape(32.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            errorRes?.let {
                Spacer(Modifier.height(8.dp))
                Text(text = stringResource(it), color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(16.dp))

            Button(onClick = ::submit, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(
                        if (mode == LockScreenMode.CREATE_PIN) R.string.settings_lock_set_pin_action else R.string.action_unlock,
                    ),
                )
            }

            if (canUseBiometric && mode == LockScreenMode.UNLOCK) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onRequestBiometric(onUnlock) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.settings_lock_use_biometric_action))
                }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        onRequestBiometric {
                            mode = LockScreenMode.CREATE_PIN
                            pin = ""
                            confirmPin = ""
                            errorRes = null
                        }
                    },
                ) {
                    Text(stringResource(R.string.settings_lock_forgot_pin_action))
                }
            }

            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onCancel) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    }
}
