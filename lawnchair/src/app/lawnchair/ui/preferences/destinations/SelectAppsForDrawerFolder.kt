package app.lawnchair.ui.preferences.destinations

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.lawnchair.data.factory.ViewModelFactory
import app.lawnchair.data.folder.model.FolderViewModel
import app.lawnchair.preferences2.ReloadHelper
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.AppItem
import app.lawnchair.ui.preferences.components.AppItemPlaceholder
import app.lawnchair.ui.preferences.components.layout.PreferenceLazyColumn
import app.lawnchair.ui.preferences.components.layout.PreferenceScaffold
import app.lawnchair.ui.preferences.components.layout.preferenceGroupItems
import app.lawnchair.util.App
import app.lawnchair.util.appComparator
import app.lawnchair.util.appsState
import com.android.launcher3.R
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.ItemInfo
import java.util.Comparator.comparing

@Composable
fun drawerFoldersComparator(hiddenApps: Set<String>): Comparator<App> = remember {
    comparing<App, Int> {
        if (hiddenApps.contains(it.key.toString())) 0 else 1
    }.then(appComparator)
}

@Composable
fun SelectAppsForDrawerFolder(
    folderInfoId: Int?,
    modifier: Modifier = Modifier,
    viewModel: FolderViewModel = viewModel(factory = ViewModelFactory(LocalContext.current) { FolderViewModel(it) }),
) {
    if (folderInfoId == null) return

    val context = LocalContext.current
    val reloadHelper = ReloadHelper(context)

    val folderInfo by viewModel.folderInfo.collectAsState()

    var selectedApps by remember { mutableStateOf(setOf<ItemInfo>()) }

    LaunchedEffect(folderInfoId) {
        viewModel.setFolderInfo(folderInfoId, false)
    }

    LaunchedEffect(folderInfo) {
        val folderContents = folderInfo?.getContents()?.toMutableSet() ?: mutableSetOf()
        selectedApps = folderContents
        viewModel.setItems(folderInfoId)
    }

    val dbItems by viewModel.items.collectAsState()
    val apps by appsState(
        comparator = drawerFoldersComparator(dbItems),
    )

    val loading = (folderInfo == null) && apps.isEmpty()
    val label = if (loading) {
        stringResource(R.string.loading)
    } else {
        folderInfo?.title.toString() + " (" + selectedApps.size + ")"
    }

    PreferenceScaffold(
        label = label,
        modifier = modifier,
        isExpandedScreen = LocalIsExpandedScreen.current,
    ) {
        Crossfade(targetState = loading, label = "") { isLoading ->
            if (isLoading) {
                PreferenceLazyColumn(it, enabled = false, state = rememberLazyListState()) {
                    preferenceGroupItems(
                        count = 20,
                        isFirstChild = true,
                        dividerStartIndent = 40.dp,
                    ) {
                        AppItemPlaceholder {
                            Spacer(Modifier.width(24.dp))
                        }
                    }
                }
            } else {
                PreferenceLazyColumn(it, state = rememberLazyListState()) {
                    val updateFolderApp = { app: App ->
                        val newSet = selectedApps.toMutableSet().apply {
                            val isChecked = any { it is AppInfo && it.targetPackage == app.key.componentName.packageName }
                            if (isChecked) {
                                removeIf { it is AppInfo && it.targetPackage == app.key.componentName.packageName }
                            } else {
                                add(
                                    app.toAppInfo(context),
                                )
                            }
                        }

                        selectedApps = newSet

                        viewModel.updateFolderWithItems(
                            folderInfoId,
                            folderInfo?.title.toString(),
                            newSet.filterIsInstance<AppInfo>().toList(),
                        )
                        viewModel.refreshFolders()
                        reloadHelper.reloadGrid()
                    }

                    preferenceGroupItems(apps, isFirstChild = true, dividerStartIndent = 40.dp) { _, app ->
                        key(app.toString()) {
                            AppItem(
                                app,
                                onClick = updateFolderApp,
                            ) {
                                Checkbox(
                                    checked = selectedApps.any {
                                        val appInfo = it as? AppInfo
                                        appInfo?.targetPackage == app.key.componentName.packageName
                                    },
                                    onCheckedChange = null,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
