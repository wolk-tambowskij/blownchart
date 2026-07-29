package app.lawnchair.ui.preferences.destinations

import android.content.Context
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.lawnchair.data.folder.model.FolderViewModel
import app.lawnchair.ui.OverflowMenu
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.AppItem
import app.lawnchair.ui.preferences.components.AppItemPlaceholder
import app.lawnchair.ui.preferences.components.layout.PreferenceDivider
import app.lawnchair.ui.preferences.components.layout.PreferenceLazyColumn
import app.lawnchair.ui.preferences.components.layout.PreferenceScaffold
import app.lawnchair.ui.preferences.components.layout.preferenceGroupItems
import app.lawnchair.util.App
import app.lawnchair.util.appsState
import com.android.launcher3.R
import com.android.launcher3.util.ComponentKey

@Composable
fun SelectAppsForDrawerFolder(
    folderInfoId: Int?,
    modifier: Modifier = Modifier,
    viewModel: FolderViewModel = viewModel(),
) {
    if (folderInfoId == null) {
        val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
        backDispatcher?.onBackPressed()
        return
    }

    val context = LocalContext.current
    val apps by appsState()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val folderInfo by viewModel.folderInfo.collectAsStateWithLifecycle()

    var allFolderPackages by remember { mutableStateOf(emptySet<String>()) }
    var filterNonUniqueItems by remember { mutableStateOf(true) }
    var hasChanges by remember { mutableStateOf(false) }

    var selectedIds by remember(folderInfo) {
        mutableStateOf(
            folderInfo?.getContents()
                ?.map { ComponentKey(it.targetComponent, it.user).toString() }
                ?.toSet()
                ?: emptySet(),
        )
    }

    // Excludes the folder being edited: its own membership is already tracked via selectedIds,
    // which updates synchronously on toggle. Including it here too would leave allFolderPackages
    // briefly stale (it only catches up once the DB write round-trips through the folders flow),
    // causing a just-toggled item to flicker out and back in.
    LaunchedEffect(folders, folderInfoId) {
        allFolderPackages = folders.filter { it.id != folderInfoId }
            .flatMap { it.getContents() }
            .mapNotNull { it.targetPackage }
            .toSet()
    }

    LaunchedEffect(folderInfoId) {
        viewModel.setFolderInfo(folderInfoId, false)
    }

    // Applying every toggle to the grid immediately would mean a full launcher model reload
    // per checkbox tap; instead apply them all once, when the user actually leaves this screen.
    DisposableEffect(Unit) {
        onDispose { if (hasChanges) viewModel.onFolderEditingFinished() }
    }

    // Apps are already sorted alphabetically by appsState(); folder membership is a filter/toggle
    // only, not a manual order, so the displayed order never changes when items are (de)selected.
    val displayedApps = remember(apps, filterNonUniqueItems, allFolderPackages, selectedIds) {
        apps.filter { app ->
            !filterNonUniqueItems ||
                !allFolderPackages.contains(app.key.componentName.packageName) ||
                selectedIds.contains(app.key.toString())
        }
    }

    fun persistSelection(newSelectedIds: Set<String>) {
        selectedIds = newSelectedIds
        hasChanges = true
        val newSelection = newSelectedIds.mapNotNull { keyString ->
            apps.find { it.key.toString() == keyString }?.toAppInfo(context)
        }
        viewModel.updateFolderItems(folderInfoId, folderInfo?.title.toString(), newSelection)
    }

    val loading = folderInfo == null && apps.isEmpty()

    PreferenceScaffold(
        label = if (loading) {
            stringResource(R.string.loading)
        } else {
            stringResource(R.string.x_with_y_count, folderInfo?.title.toString(), selectedIds.size)
        },
        modifier = modifier,
        actions = {
            if (!loading) {
                OverflowMenu {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.inverse_selection)) },
                        onClick = {
                            // Invert selection within the currently displayed (filtered) apps only;
                            // selections outside the current filter are left untouched.
                            val displayedIds = displayedApps.map { it.key.toString() }.toSet()
                            persistSelection((selectedIds - displayedIds) + (displayedIds - selectedIds))
                            hideMenu()
                        },
                    )
                    val allSelected = displayedApps.isNotEmpty() && displayedApps.all { selectedIds.contains(it.key.toString()) }
                    DropdownMenuItem(
                        text = { Text(stringResource(if (allSelected) R.string.deselect_all else R.string.select_all)) },
                        onClick = {
                            val displayedIds = displayedApps.map { it.key.toString() }.toSet()
                            persistSelection(if (allSelected) selectedIds - displayedIds else selectedIds + displayedIds)
                            hideMenu()
                        },
                    )
                    PreferenceDivider(modifier = Modifier.padding(vertical = 8.dp))
                    DropdownMenuItem(
                        onClick = {
                            filterNonUniqueItems = !filterNonUniqueItems
                            hideMenu()
                        },
                        trailingIcon = {
                            if (filterNonUniqueItems) Icon(Icons.Rounded.Check, null)
                        },
                        text = { Text(stringResource(R.string.folders_filter_duplicates)) },
                    )
                }
            }
        },
        isExpandedScreen = LocalIsExpandedScreen.current,
    ) { contentPadding ->
        Crossfade(targetState = loading, label = "") { isLoading ->
            if (isLoading) {
                PreferenceLazyColumn(contentPadding, enabled = false, state = rememberLazyListState()) {
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
                PreferenceLazyColumn(contentPadding, state = rememberLazyListState()) {
                    preferenceGroupItems(
                        items = displayedApps,
                        isFirstChild = true,
                        dividerStartIndent = 40.dp,
                    ) { _, app ->
                        val isSelected = selectedIds.contains(app.key.toString())
                        AppItem(
                            app = app,
                            onClick = { toggledApp: App ->
                                val key = toggledApp.key.toString()
                                persistSelection(if (isSelected) selectedIds - key else selectedIds + key)
                            },
                            endWidget = {
                                Checkbox(checked = isSelected, onCheckedChange = null)
                            },
                        )
                    }
                }
            }
        }
    }
}
