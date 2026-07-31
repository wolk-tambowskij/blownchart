package app.lawnchair.ui.preferences.destinations

import android.content.Context
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.OverflowMenu
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.AppItem
import app.lawnchair.ui.preferences.components.AppItemPlaceholder
import app.lawnchair.ui.preferences.components.layout.PreferenceDivider
import app.lawnchair.ui.preferences.components.layout.PreferenceLazyColumn
import app.lawnchair.ui.preferences.components.layout.PreferenceScaffold
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.ui.preferences.components.layout.preferenceGroupItems
import app.lawnchair.ui.preferences.components.reorderable.ReorderableDragHandle
import app.lawnchair.ui.preferences.components.reorderable.ReorderablePreferenceGroup
import app.lawnchair.util.App
import app.lawnchair.util.appsState
import com.android.launcher3.R
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.util.ComponentKey
import java.util.Locale

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
    val allApps by appsState()
    val hiddenApps by preferenceManager2().hiddenApps.getAdapter().state
    // Apps the user has hidden (including the launcher's own entry, hidden by default) shouldn't
    // clutter the folder picker - they're still reachable elsewhere (e.g. an existing folder that
    // already contains one keeps showing it via selectedIds, untouched by this filter).
    val apps = remember(allApps, hiddenApps) { allApps.filter { !hiddenApps.contains(it.key.toString()) } }
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val folderInfo by viewModel.folderInfo.collectAsStateWithLifecycle()

    var allFolderPackages by remember { mutableStateOf(emptySet<String>()) }
    var filterNonUniqueItems by remember { mutableStateOf(true) }
    var hasChanges by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val manualOrder by preferenceManager2().folderManualOrder.getAdapter().state

    var selectedIds by remember(folderInfo) {
        mutableStateOf(
            folderInfo?.getContents()
                ?.filterIsInstance<AppInfo>()
                ?.map { ComponentKey(it.targetComponent, it.user).toString() }
                ?.toSet()
                ?: emptySet(),
        )
    }

    // The folder's nested subfolder, if it has one. It always renders as its own block before
    // every app (see FolderService.mapToFolderInfo()) and isn't itself reorderable from here -
    // its position relative to the apps below is fixed, only shown here for context.
    val nestedSubfolder = folderInfo?.getContents()?.filterIsInstance<FolderInfo>()?.firstOrNull()

    // Ordered view of the selected apps, for manual-order dragging. Seeded once from the
    // folder's current (already rank-ordered, see FolderService) contents, then maintained by
    // hand as checkboxes toggle - re-deriving it from folderInfo on every change would pick up
    // FolderViewModel's alphabetically-rebuilt local state and undo any in-progress drag.
    var selectedOrder by remember(folderInfoId) { mutableStateOf<List<App>>(emptyList()) }
    var orderInitialized by remember(folderInfoId) { mutableStateOf(false) }
    var hasOrderChanges by remember { mutableStateOf(false) }

    LaunchedEffect(folderInfo, apps) {
        val info = folderInfo
        if (!orderInitialized && info != null && apps.isNotEmpty()) {
            selectedOrder = info.getContents().filterIsInstance<AppInfo>().mapNotNull { itemInfo ->
                val key = ComponentKey(itemInfo.targetComponent, itemInfo.user).toString()
                apps.find { it.key.toString() == key }
            }
            orderInitialized = true
        }
    }

    // Excludes the folder being edited: its own membership is already tracked via selectedIds,
    // which updates synchronously on toggle. Including it here too would leave allFolderPackages
    // briefly stale (it only catches up once the DB write round-trips through the folders flow),
    // causing a just-toggled item to flicker out and back in.
    LaunchedEffect(folders, folderInfoId) {
        allFolderPackages = folders.filter { it.id != folderInfoId }
            .flatMap { it.getContents() }
            .filterIsInstance<AppInfo>()
            .mapNotNull { it.targetPackage }
            .toSet()
    }

    LaunchedEffect(folderInfoId) {
        viewModel.setFolderInfo(folderInfoId, false)
    }

    // Applying every toggle to the grid immediately would mean a full launcher model reload
    // per checkbox tap; instead apply them all once, when the user actually leaves this screen.
    // The manual item order is likewise only written once here, not per drag settle.
    DisposableEffect(Unit) {
        onDispose {
            if (hasOrderChanges) {
                viewModel.updateFolderItemOrder(folderInfoId, selectedOrder.map { it.key.toString() })
            }
            if (hasChanges || hasOrderChanges) viewModel.onFolderEditingFinished()
        }
    }

    // Apps are already sorted alphabetically by appsState(); folder membership is a filter/toggle
    // only, not a manual order, so the displayed order never changes when items are (de)selected.
    val displayedApps = remember(apps, filterNonUniqueItems, allFolderPackages, selectedIds, searchQuery) {
        apps.filter { app ->
            (
                !filterNonUniqueItems ||
                    !allFolderPackages.contains(app.key.componentName.packageName) ||
                    selectedIds.contains(app.key.toString())
                ) &&
                app.label.contains(searchQuery, ignoreCase = true)
        }
    }

    fun persistSelection(newSelectedIds: Set<String>) {
        selectedIds = newSelectedIds
        hasChanges = true
        // Keep the drag-order list in sync: drop deselected apps, append newly selected ones at
        // the end (their position can be dragged into place afterward).
        selectedOrder = selectedOrder.filter { newSelectedIds.contains(it.key.toString()) } +
            apps.filter { app ->
                newSelectedIds.contains(app.key.toString()) &&
                    selectedOrder.none { it.key.toString() == app.key.toString() }
            }
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
                    item {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            placeholder = { Text(stringResource(R.string.all_apps_search_bar_hint)) },
                            leadingIcon = { Icon(Icons.Rounded.Search, null) },
                            trailingIcon = if (searchQuery.isNotEmpty()) {
                                {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Rounded.Clear, null)
                                    }
                                }
                            } else {
                                null
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(32.dp),
                        )
                    }
                    if (nestedSubfolder != null) {
                        item {
                            // Informational only - always sorts before every app (see
                            // FolderService.mapToFolderInfo()), not draggable from here. Manage
                            // its own position or contents from the folder list instead.
                            PreferenceTemplate(
                                title = { Text(nestedSubfolder.title.toString()) },
                                startWidget = {
                                    Icon(Icons.Rounded.Folder, contentDescription = null, modifier = Modifier.size(30.dp))
                                },
                                verticalPadding = 12.dp,
                            )
                        }
                    }
                    if (manualOrder && selectedOrder.isNotEmpty()) {
                        item {
                            ReorderablePreferenceGroup(
                                label = stringResource(R.string.folder_contents_heading),
                                items = selectedOrder,
                                defaultList = remember(selectedOrder) {
                                    selectedOrder.sortedBy { it.label.lowercase(Locale.getDefault()) }
                                },
                                onOrderChange = {
                                    selectedOrder = it
                                    hasOrderChanges = true
                                },
                            ) { app, _, _, onDraggingChange ->
                                AppItem(
                                    app = app,
                                    onClick = {},
                                    widget = {
                                        ReorderableDragHandle(
                                            scope = this,
                                            onDragStop = { onDraggingChange(false) },
                                        )
                                    },
                                )
                            }
                        }
                    }
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
