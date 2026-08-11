package app.blownchart.util

import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi
import app.blownchart.gestures.handlers.SleepMethodDeviceAdmin
import com.android.launcher3.R

fun Context.openAppPermissionSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
    val uri: Uri = Uri.fromParts("package", packageName, null)
    intent.data = uri

    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
    intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)

    if (intent.resolveActivity(packageManager) != null) {
        startActivity(intent)
    } else {
        Log.e("Permissions", "No activity found to handle application details settings intent")
    }
}

/**
 * Whether this app currently has usage access ("Usage access" in Settings) granted. Despite
 * PACKAGE_USAGE_STATS being declared as a normal manifest permission, third-party apps are
 * actually gated on it through [AppOpsManager], not the regular permission-grant system - there
 * is no way to auto-grant it via the manifest, and [Context.checkCallingOrSelfPermission] for
 * this specific permission reliably returns DENIED regardless of whether the user has actually
 * enabled it, since its declared protection level was never meant to be satisfied by a normal
 * grant dialog in the first place. This is the correct way to check it.
 */
fun Context.hasUsageStatsAccess(): Boolean {
    val appOps = getSystemService(AppOpsManager::class.java) ?: return false

    @Suppress("DEPRECATION")
    val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
    return mode == AppOpsManager.MODE_ALLOWED
}

fun Context.openUsageAccessSettings() {
    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (intent.resolveActivity(packageManager) != null) {
        startActivity(intent)
    } else {
        Log.e("Permissions", "No activity found to handle usage access settings intent")
    }
}

/**
 * Whether BlownChart is currently active as a device admin app (reusing the same admin receiver
 * as the "Double tap to sleep" gesture - see [SleepMethodDeviceAdmin]). Some OEMs are less
 * aggressive about killing background processes/services belonging to an app that holds device
 * admin, which is also relevant to keeping the Recents accessibility service alive.
 */
fun Context.isDeviceAdminActive(): Boolean {
    val devicePolicyManager = getSystemService(DevicePolicyManager::class.java) ?: return false
    val admin = ComponentName(this, SleepMethodDeviceAdmin.SleepDeviceAdmin::class.java)
    return devicePolicyManager.isAdminActive(admin)
}

fun Context.requestDeviceAdmin() {
    val admin = ComponentName(this, SleepMethodDeviceAdmin.SleepDeviceAdmin::class.java)
    val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
        .putExtra(
            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
            getString(R.string.recents_button_interception_device_admin_hint),
        )
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    if (intent.resolveActivity(packageManager) != null) {
        startActivity(intent)
    } else {
        Log.e("Permissions", "No activity found to handle add device admin intent")
    }
}

@RequiresApi(Build.VERSION_CODES.R)
fun Context.requestManageAllFilesAccessPermission() {
    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
    intent.data = Uri.fromParts("package", packageName, null)

    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
    intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)

    if (intent.resolveActivity(packageManager) != null) {
        startActivity(intent)
    } else {
        Log.e("Permissions", "No activity found to handle application details settings intent")
    }
}
