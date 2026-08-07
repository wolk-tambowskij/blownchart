package app.blownchart.ui.preferences.destinations

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Process
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material.icons.rounded.TipsAndUpdates
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import app.blownchart.BlownChartApp
import app.blownchart.BlownChartLauncher
import app.blownchart.backup.ui.restoreBackupOpener
import app.blownchart.backup.ui.restoreNovaBackupOpener
import app.blownchart.preferences.getAdapter
import app.blownchart.preferences.observeAsState
import app.blownchart.preferences.preferenceManager
import app.blownchart.preferences2.preferenceManager2
import app.blownchart.ui.OverflowMenu
import app.blownchart.ui.preferences.LocalNavController
import app.blownchart.ui.preferences.components.controls.PreferenceCategory
import app.blownchart.ui.preferences.components.controls.WarningPreference
import app.blownchart.ui.preferences.components.layout.ClickableIcon
import app.blownchart.ui.preferences.components.layout.DividerColumn
import app.blownchart.ui.preferences.components.layout.PreferenceDivider
import app.blownchart.ui.preferences.components.layout.PreferenceLayout
import app.blownchart.ui.preferences.components.layout.PreferenceTemplate
import app.blownchart.ui.preferences.navigation.About
import app.blownchart.ui.preferences.navigation.AppDrawer
import app.blownchart.ui.preferences.navigation.CreateBackup
import app.blownchart.ui.preferences.navigation.DebugMenu
import app.blownchart.ui.preferences.navigation.Dock
import app.blownchart.ui.preferences.navigation.ExperimentalFeatures
import app.blownchart.ui.preferences.navigation.Folders
import app.blownchart.ui.preferences.navigation.General
import app.blownchart.ui.preferences.navigation.Gestures
import app.blownchart.ui.preferences.navigation.HomeScreen
import app.blownchart.ui.preferences.navigation.PreferenceRootRoute
import app.blownchart.ui.preferences.navigation.Quickstep
import app.blownchart.ui.preferences.navigation.Search
import app.blownchart.ui.preferences.navigation.Smartspace
import app.blownchart.ui.theme.isSelectedThemeDark
import app.blownchart.ui.theme.preferenceGroupColor
import app.blownchart.ui.util.addIf
import app.blownchart.util.isDefaultLauncher
import app.blownchart.util.isIgnoringBatteryOptimizations
import app.blownchart.util.lifecycleState
import app.blownchart.util.restartLauncher
import com.android.launcher3.BuildConfig
import com.android.launcher3.R

@Composable
fun PreferencesDashboard(
    currentRoute: PreferenceRootRoute,
    onNavigate: (PreferenceRootRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val pref2 = preferenceManager2()

    PreferenceLayout(
        label = stringResource(id = R.string.settings),
        modifier = modifier,
        verticalArrangement = Arrangement.Top,
        backArrowVisible = false,
        actions = { PreferencesOverflowMenu(currentRoute = currentRoute, onNavigate = onNavigate) },
    ) {
        if (BuildConfig.APPLICATION_ID.contains("nightly") || BuildConfig.DEBUG) {
            PreferencesDebugWarning()
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (!context.isDefaultLauncher()) {
            PreferencesSetDefaultLauncherWarning()
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Unlike the default-launcher warning, this screen doesn't finish() itself when the user
        // taps through to system settings and back - so a plain function call here would only
        // ever be evaluated once and never notice the permission was granted. Re-check on every
        // lifecycle change (e.g. the ON_RESUME when returning from that settings screen).
        val ignoringBatteryOptimizations = remember(lifecycleState()) { context.isIgnoringBatteryOptimizations() }
        if (!ignoringBatteryOptimizations) {
            PreferencesBatteryOptimizationWarning()
            Spacer(modifier = Modifier.height(8.dp))
        }

        PreferenceCategoryGroup {
            PreferenceCategory(
                label = stringResource(R.string.general_label),
                description = stringResource(R.string.general_description),
                iconResource = R.drawable.ic_general,
                onNavigate = { onNavigate(General) },
                isSelected = currentRoute is General,
            )

            PreferenceCategory(
                label = stringResource(R.string.home_screen_label),
                description = stringResource(R.string.home_screen_description),
                iconResource = R.drawable.ic_home_screen,
                onNavigate = { onNavigate(HomeScreen) },
                isSelected = currentRoute is HomeScreen,
            )

            PreferenceCategory(
                label = stringResource(id = R.string.smartspace_widget),
                description = stringResource(R.string.smartspace_widget_description),
                iconResource = R.drawable.ic_smartspace,
                onNavigate = { onNavigate(Smartspace) },
                isSelected = currentRoute is Smartspace,
            )

            PreferenceCategory(
                label = stringResource(R.string.dock_label),
                description = stringResource(R.string.dock_description),
                iconResource = R.drawable.ic_dock,
                onNavigate = { onNavigate(Dock) },
                isSelected = currentRoute is Dock,
            )

            val deckLayout = pref2.deckLayout.getAdapter()
            if (!deckLayout.state.value) {
                PreferenceCategory(
                    label = stringResource(R.string.app_drawer_label),
                    description = stringResource(R.string.app_drawer_description),
                    iconResource = R.drawable.ic_app_drawer,
                    onNavigate = { onNavigate(AppDrawer) },
                    isSelected = currentRoute is AppDrawer,
                )
            }

            PreferenceCategory(
                label = stringResource(R.string.search_bar_label),
                description = stringResource(R.string.drawer_search_description),
                iconResource = R.drawable.ic_search,
                onNavigate = { onNavigate(Search()) },
                isSelected = currentRoute is Search,
            )

            PreferenceCategory(
                label = stringResource(R.string.folders_label),
                description = stringResource(R.string.folders_description),
                iconResource = R.drawable.ic_folder,
                onNavigate = { onNavigate(Folders) },
                isSelected = currentRoute is Folders,
            )

            PreferenceCategory(
                label = stringResource(id = R.string.gestures_label),
                description = stringResource(R.string.gestures_description),
                iconResource = R.drawable.ic_gestures,
                onNavigate = { onNavigate(Gestures) },
                isSelected = currentRoute is Gestures,
            )

            if (BlownChartApp.isRecentsEnabled || BuildConfig.DEBUG) {
                PreferenceCategory(
                    label = stringResource(id = R.string.quickstep_label),
                    description = stringResource(id = R.string.quickstep_description),
                    iconResource = R.drawable.ic_quickstep,
                    onNavigate = { onNavigate(Quickstep) },
                    isSelected = currentRoute is Quickstep,
                )
            }

            PreferenceCategory(
                label = stringResource(R.string.about_label),
                description = "${context.getString(R.string.derived_app_name)} ${BuildConfig.MAJOR_VERSION}",
                iconResource = R.drawable.ic_about,
                onNavigate = { onNavigate(About) },
                isSelected = currentRoute is About,
            )
        }
    }
}

@Composable
fun PreferenceCategoryGroup(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val color = preferenceGroupColor()

    Surface(
        modifier = modifier.padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        color = color,
        tonalElevation = if (isSelectedThemeDark) 1.dp else 0.dp,
    ) {
        DividerColumn(
            content = content,
            startIndent = (-16).dp,
            endIndent = (-16).dp,
            color = MaterialTheme.colorScheme.surface,
            thickness = 2.dp,
        )
    }
}

@Composable
fun RowScope.PreferencesOverflowMenu(
    currentRoute: PreferenceRootRoute,
    onNavigate: (PreferenceRootRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    val enableDebug by preferenceManager().enableDebugMenu.observeAsState()
    val highlightColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
    val highlightShape = MaterialTheme.shapes.large

    if (enableDebug) {
        ClickableIcon(
            imageVector = Icons.Rounded.Build,
            onClick = { onNavigate(DebugMenu) },
            modifier = Modifier.addIf(currentRoute == DebugMenu) {
                Modifier
                    .clip(highlightShape)
                    .background(highlightColor)
            },
        )
    }
    val navController = LocalNavController.current
    val openCreateBackup = { navController.navigate(CreateBackup) }
    val openRestoreBackup = restoreBackupOpener()
    val openRestoreNovaBackup = restoreNovaBackupOpener()
    OverflowMenu(
        modifier = modifier.addIf(
            listOf(ExperimentalFeatures).any {
                currentRoute == it
            },
        ) {
            Modifier
                .clip(highlightShape)
                .background(highlightColor)
        },
    ) {
        val context = LocalContext.current
        DropdownMenuItem(
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_about),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            onClick = {
                openAppInfo(context)
                hideMenu()
            },
            text = {
                Text(text = stringResource(id = R.string.app_info_drop_target_label))
            },
        )
        DropdownMenuItem(
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            onClick = {
                restartLauncher(context)
                hideMenu()
            },
            text = {
                Text(text = stringResource(id = R.string.debug_restart_launcher))
            },
        )
        DropdownMenuItem(
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Science,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            onClick = {
                onNavigate(ExperimentalFeatures)
                hideMenu()
            },
            text = {
                Text(text = stringResource(id = R.string.experimental_features_label))
            },
        )
        PreferenceDivider(modifier = Modifier.padding(vertical = 8.dp))
        DropdownMenuItem(
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Backup,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            onClick = {
                openCreateBackup()
                hideMenu()
            },
            text = {
                Text(text = stringResource(id = R.string.create_backup))
            },
        )
        DropdownMenuItem(
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.SettingsBackupRestore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            onClick = {
                openRestoreBackup()
                hideMenu()
            },
            text = {
                Text(text = stringResource(id = R.string.restore_backup))
            },
        )
        DropdownMenuItem(
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.SettingsBackupRestore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            onClick = {
                openRestoreNovaBackup()
                hideMenu()
            },
            text = {
                Text(text = stringResource(id = R.string.restore_nova_backup))
            },
        )
    }
}

@Composable
fun PreferencesDebugWarning(
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        WarningPreference(
            // Don't move to strings.xml, no need to translate this warning
            text = "You are using a development build, which may contain bugs and broken features. Use at your own risk!",
        )
    }
}

@Composable
fun PreferencesSetDefaultLauncherWarning(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Surface(
        modifier = modifier.padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        PreferenceTemplate(
            modifier = Modifier.clickable {
                Intent(Settings.ACTION_HOME_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .let { context.startActivity(it) }
                (context as? Activity)?.finish()
            },
            title = {},
            description = {
                Text(
                    text = stringResource(id = R.string.set_default_launcher_tip),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            startWidget = {
                Icon(
                    imageVector = Icons.Rounded.TipsAndUpdates,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    contentDescription = null,
                )
            },
        )
    }
}

@Composable
fun PreferencesBatteryOptimizationWarning(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Surface(
        modifier = modifier.padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        PreferenceTemplate(
            modifier = Modifier.clickable {
                runCatching {
                    Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:${context.packageName}"),
                    ).let { context.startActivity(it) }
                }
            },
            title = {},
            description = {
                Text(
                    text = stringResource(id = R.string.battery_optimization_tip),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            startWidget = {
                Icon(
                    imageVector = Icons.Rounded.TipsAndUpdates,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    contentDescription = null,
                )
            },
        )
    }
}

fun openAppInfo(context: Context) {
    val launcherApps = context.getSystemService<LauncherApps>()
    val componentName = ComponentName(context, BlownChartLauncher::class.java)
    launcherApps?.startAppDetailsActivity(componentName, Process.myUserHandle(), null, null)
}
