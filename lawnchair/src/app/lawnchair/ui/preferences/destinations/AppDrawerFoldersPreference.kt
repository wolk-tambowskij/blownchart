package app.lawnchair.ui.preferences.destinations

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.lawnchair.data.folder.model.FolderOrderUtils
import app.lawnchair.data.folder.model.FolderViewModel
import app.lawnchair.data.folder.service.FolderListEntry
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.ModalBottomSheetContent
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.layout.LoadingScreen
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.ui.preferences.components.reorderable.ReorderableDragHandle
import app.lawnchair.ui.preferences.components.reorderable.ReorderablePreferenceGroup
import app.lawnchair.ui.preferences.navigation.AppDrawerAppListToFolder
import app.lawnchair.ui.preferences.navigation.AppDrawerFolder
import app.lawnchair.ui.util.bottomSheetHandler
import app.lawnchair.util.appsState
import com.android.launcher3.R
import com.android.launcher3.model.data.FolderInfo

@Composable
fun AppDrawerFolderPreferenceItem(
    modifier: Modifier = Modifier,
) {
    val navController = LocalNavController.current

    PreferenceGroup(
        modifier = modifier,
    ) {
        ClickablePreference(
            label = stringResource(R.string.app_drawer_folder),
            modifier = Modifier,
            onClick = {
                navController.navigate(route = AppDrawerFolder)
            },
        )
    }
}

@Composable
fun AppDrawerFoldersPreference(
    modifier: Modifier = Modifier,
    viewModel: FolderViewModel = viewModel(),
) {
    val navController = LocalNavController.current
    val entries by viewModel.flatFolders.collectAsStateWithLifecycle()
    val nestableFolders by viewModel.nestableFolders.collectAsStateWithLifecycle()

    AppDrawerFoldersPreference(
        modifier = modifier,
        entries = entries,
        nestableFolders = nestableFolders,
        onRequestNestableFolders = { viewModel.loadNestableFolders(it) },
        onCreateFolder = { folderInfo, label ->
            val newInfo = folderInfo.apply {
                title = label
            }
            viewModel.createFolder(newInfo)
        },
        onEditFolderItems = {
            viewModel.setFolderInfo(it, false)
            navController.navigate(AppDrawerAppListToFolder(it))
        },
        onRenameFolder = { folderInfo, it ->
            folderInfo.apply {
                title = it
                viewModel.renameFolder(this, false)
            }
        },
        onDeleteFolder = {
            viewModel.deleteFolder(it.id)
        },
        onSetParent = { folderInfo, parentId, onResult ->
            viewModel.setParentFolder(folderInfo.id, parentId, onResult)
        },
    )
}

@Composable
fun AppDrawerFoldersPreference(
    entries: List<FolderListEntry>,
    nestableFolders: List<FolderInfo>,
    onRequestNestableFolders: (Int) -> Unit,
    onCreateFolder: (FolderInfo, String) -> Unit,
    onEditFolderItems: (Int) -> Unit,
    onRenameFolder: (FolderInfo, String) -> Unit,
    onDeleteFolder: (FolderInfo) -> Unit,
    onSetParent: (FolderInfo, Int?, (Boolean) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bottomSheetHandler = bottomSheetHandler
    val prefs = preferenceManager()
    val folderOrderAdapter = prefs.drawerListOrder.getAdapter()

    val folderOrderString by folderOrderAdapter.state

    // Top-level folders are draggable among each other; a folder's own nested subfolder(s), if
    // any, render directly beneath it, indented, and aren't part of this reorderable list
    // themselves - un-nesting first is how you'd move one to a different position in it.
    val topLevelEntries = entries.filter { it.parentFolderId == null }
    val childrenByParentId = entries
        .filter { it.parentFolderId != null }
        .groupBy { it.parentFolderId!! }

    var sortedDisplayList = remember(topLevelEntries, folderOrderString) {
        Log.d("AppDrawerFolders", "Recalculating sortedDisplayList. Folders count: ${topLevelEntries.size}")
        topLevelEntries.sortedWith(
            compareBy { entry ->
                val index = FolderOrderUtils
                    .stringToIntList(folderOrderString)
                    .indexOf(entry.folderInfo.id)
                if (index == -1) {
                    // New items go to the end
                    Integer.MAX_VALUE
                } else {
                    index
                }
            },
        )
    }

    val apps by appsState()

    LoadingScreen(
        isLoading = apps.isEmpty(),
        modifier = modifier.fillMaxWidth(),
    ) {
        PreferenceLayout(
            label = stringResource(id = R.string.app_drawer_folder),
            backArrowVisible = true,
        ) {
            PreferenceGroup(
                heading = stringResource(R.string.settings),
            ) {
                SwitchPreference(
                    adapter = prefs.folderApps.getAdapter(),
                    label = stringResource(id = R.string.apps_in_folder_label),
                    description = stringResource(id = R.string.apps_in_folder_description),
                )
            }
            PreferenceGroup(heading = stringResource(R.string.folders_label)) {
                PreferenceTemplate(
                    title = {},
                    description = {
                        Text(
                            text = stringResource(R.string.add_folder),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    modifier = Modifier.clickable {
                        bottomSheetHandler.show {
                            FolderEditSheet(
                                FolderInfo().apply {
                                    title = stringResource(R.string.my_folder_label)
                                },
                                parentFolderId = null,
                                nestableFolders = emptyList(),
                                onRequestNestableFolders = {},
                                onRename = onCreateFolder,
                                onSetParent = { _, _, _ -> },
                                onNavigate = {},
                                onDismiss = {
                                    bottomSheetHandler.hide()
                                },
                                hideAppPicker = true,
                            )
                        }
                    },
                    startWidget = {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                    },
                )
            }
            ReorderablePreferenceGroup(
                label = null,
                items = sortedDisplayList,
                defaultList = sortedDisplayList,
                onOrderChange = { folders ->
                    val newOrder = folders.map { it.folderInfo.id }

                    folderOrderAdapter.onChange(
                        FolderOrderUtils.intListToString(
                            newOrder,
                        ),
                    )
                    sortedDisplayList = folders
                },
            ) { entry, _, _, onDraggingChange ->
                val interactionSource = remember { MutableInteractionSource() }
                val folderInfo = entry.folderInfo
                val children = childrenByParentId[folderInfo.id].orEmpty()

                fun openEditSheet(target: FolderInfo, parentId: Int?) {
                    onRequestNestableFolders(target.id)
                    bottomSheetHandler.show {
                        FolderEditSheet(
                            target,
                            parentFolderId = parentId,
                            nestableFolders = nestableFolders,
                            onRequestNestableFolders = { onRequestNestableFolders(target.id) },
                            onRename = onRenameFolder,
                            onSetParent = onSetParent,
                            onNavigate = {
                                onEditFolderItems(it)
                                bottomSheetHandler.hide()
                            },
                            onDismiss = {
                                bottomSheetHandler.hide()
                            },
                        )
                    }
                }

                // Captured before entering the Column below, whose own content lambda has a
                // ColumnScope receiver that would otherwise shadow this ReorderableScope.
                val reorderableScope = this
                Column {
                    FolderItem(
                        folderInfo = folderInfo,
                        childCount = children.size,
                        onItemClick = { openEditSheet(folderInfo, null) },
                        onItemDelete = { folderToDelete ->
                            val currentOrder =
                                FolderOrderUtils.stringToIntList(folderOrderAdapter.state.value)
                            val newOrderAfterDelete =
                                currentOrder.filter { it != folderToDelete.id }
                            folderOrderAdapter.onChange(
                                FolderOrderUtils.intListToString(
                                    newOrderAfterDelete,
                                ),
                            )
                            onDeleteFolder(folderToDelete)
                        },
                        dragIndicator = {
                            ReorderableDragHandle(
                                interactionSource = interactionSource,
                                scope = reorderableScope,
                                onDragStop = {
                                    onDraggingChange(false)
                                },
                            )
                        },
                        interactionSource = interactionSource,
                    )
                    // Rendered directly under their parent, indented - not part of the
                    // reorderable list above, since a nested folder's position relative to its
                    // parent's siblings isn't a meaningful thing to drag.
                    children.forEach { child ->
                        val childInteractionSource = remember { MutableInteractionSource() }
                        FolderItem(
                            folderInfo = child.folderInfo,
                            parentFolderTitle = folderInfo.title.toString(),
                            modifier = Modifier.padding(start = 24.dp),
                            onItemClick = { openEditSheet(child.folderInfo, folderInfo.id) },
                            onItemDelete = onDeleteFolder,
                            dragIndicator = { Spacer(modifier = Modifier.size(48.dp)) },
                            interactionSource = childInteractionSource,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FolderEditSheet(
    folderInfo: FolderInfo,
    parentFolderId: Int?,
    nestableFolders: List<FolderInfo>,
    onRequestNestableFolders: () -> Unit,
    onRename: (FolderInfo, String) -> Unit,
    onSetParent: (FolderInfo, Int?, (Boolean) -> Unit) -> Unit,
    onNavigate: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    hideAppPicker: Boolean = false,
) {
    val resources = LocalContext.current.resources
    var textFieldValue by remember { mutableStateOf(TextFieldValue(folderInfo.title.toString())) }
    val currentOnRequestNestableFolders by rememberUpdatedState(onRequestNestableFolders)

    if (!hideAppPicker) {
        LaunchedEffect(folderInfo.id) {
            currentOnRequestNestableFolders()
        }
    }

    ModalBottomSheetContent(
        buttons = {
            OutlinedButton(
                onClick = onDismiss,
            ) {
                Text(stringResource(android.R.string.cancel))
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    onRename(folderInfo, textFieldValue.text)
                    onDismiss()
                },
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        modifier = modifier,
    ) {
        Column {
            OutlinedTextField(
                value = textFieldValue,
                onValueChange = {
                    textFieldValue = it
                },
                label = { Text(text = stringResource(id = R.string.label)) },
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
                singleLine = true,
                isError = textFieldValue.text.isEmpty(),
            )
            if (!hideAppPicker) {
                ClickablePreference(
                    label = "Manage apps",
                    subtitle = resources.getQuantityString(
                        R.plurals.apps_count,
                        folderInfo.getContents().size,
                        folderInfo.getContents().size,
                    ),
                    modifier = Modifier
                        .padding(horizontal = 8.dp),
                ) {
                    onNavigate(folderInfo.id)
                }
                // A folder that already has subfolders of its own can't also be nested (that
                // would put its children two levels deep), so nestableFolders comes back empty
                // for it - and with no parent and nowhere to go, there's nothing to offer here.
                if (parentFolderId != null || nestableFolders.isNotEmpty()) {
                    NestFolderPicker(
                        currentParentId = parentFolderId,
                        nestableFolders = nestableFolders,
                        onSelect = { newParentId ->
                            onSetParent(folderInfo, newParentId) {}
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun NestFolderPicker(
    currentParentId: Int?,
    nestableFolders: List<FolderInfo>,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentParentTitle = nestableFolders.find { it.id == currentParentId }?.title?.toString()

    Column(modifier = modifier) {
        ClickablePreference(
            label = "Nest inside",
            subtitle = currentParentTitle ?: "None",
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            expanded = true
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (currentParentId != null) {
                DropdownMenuItem(
                    text = { Text("None") },
                    onClick = {
                        expanded = false
                        onSelect(null)
                    },
                )
            }
            nestableFolders.forEach { candidate ->
                DropdownMenuItem(
                    text = { Text(candidate.title.toString()) },
                    onClick = {
                        expanded = false
                        onSelect(candidate.id)
                    },
                )
            }
        }
    }
}

@Composable
fun FolderItem(
    folderInfo: FolderInfo,
    onItemClick: (FolderInfo) -> Unit,
    onItemDelete: (FolderInfo) -> Unit,
    modifier: Modifier = Modifier,
    childCount: Int = 0,
    parentFolderTitle: String? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    dragIndicator: @Composable () -> Unit,
) {
    val resources = LocalContext.current.resources
    PreferenceTemplate(
        title = {
            Row {
                Text(
                    text = folderInfo.title.toString(),
                )
                // Icon cue that this folder contains a nested subfolder - mirrors the badge
                // drawn on the folder's actual closed icon in the live drawer (FolderIcon).
                if (childCount > 0) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Rounded.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        description = {
            val appsCount = resources.getQuantityString(R.plurals.apps_count, folderInfo.getContents().size, folderInfo.getContents().size)
            Text(
                text = if (parentFolderTitle != null) "$appsCount · nested in $parentFolderTitle" else appsCount,
            )
        },
        startWidget = {
            dragIndicator()
        },
        endWidget = {
            Row {
                IconButton(
                    onClick = {
                        onItemDelete(folderInfo)
                    },
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        modifier = modifier.clickable(
            interactionSource = interactionSource,
            indication = ripple(),
        ) {
            onItemClick(folderInfo)
        },
    )
}
