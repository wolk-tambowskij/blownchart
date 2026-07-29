package app.lawnchair.battery

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import app.lawnchair.preferences2.PreferenceManager2
import com.android.launcher3.R
import com.patrykmichalik.opto.core.firstBlocking
import com.patrykmichalik.opto.core.setBlocking

/**
 * One-time, best-effort nudge shown after install (or after app data is cleared, which resets
 * the "already shown" flag same as a fresh install). Android requires an explicit tap for both
 * of these - there's no way to flip either setting silently - so this only ever opens the
 * relevant system screen for the user to confirm. There's also no API to toggle OEM-specific
 * autostart/background settings (MIUI, EMUI, MTK DuraSpeed, etc.); the dialog just tells the user
 * those may exist separately, since there's nothing to deep-link to reliably across devices.
 */
object BatteryOptimizationPrompt {

    fun maybeShow(activity: Activity) {
        val prefs = PreferenceManager2.getInstance(activity)
        if (prefs.batteryOptimizationPromptShown.firstBlocking()) return
        prefs.batteryOptimizationPromptShown.setBlocking(true)

        val powerManager = activity.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (powerManager?.isIgnoringBatteryOptimizations(activity.packageName) == true) return

        val builder = AlertDialog.Builder(activity)
            .setTitle(R.string.battery_optimization_prompt_title)
            .setMessage(R.string.battery_optimization_prompt_message)
            .setPositiveButton(R.string.battery_optimization_prompt_allow) { _, _ ->
                runCatching {
                    activity.startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${activity.packageName}"),
                        ),
                    )
                }
            }
            .setNegativeButton(R.string.battery_optimization_prompt_skip, null)

        // The "remove permissions if unused" auto-revoke screen only exists on API 30+.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setNeutralButton(R.string.battery_optimization_prompt_auto_revoke) { _, _ ->
                runCatching {
                    activity.startActivity(
                        Intent(
                            Intent.ACTION_AUTO_REVOKE_PERMISSIONS,
                            Uri.parse("package:${activity.packageName}"),
                        ),
                    )
                }
            }
        }

        builder.show()
    }
}
