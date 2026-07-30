package app.lawnchair.ui.preferences.destinations

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
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
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.lawnchair.data.folder.model.FolderViewModel
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.ModalBottomSheetContent
import app.lawnchair.ui.OverflowMenu
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.ui.preferences.components.reorderable.ReorderableDragHandle
import app.lawnchair.ui.preferences.components.reorderable.ReorderablePreferenceGroup
import app.lawnchair.ui.preferences.navigation.AppDrawerAppListToFolder
import app.lawnchair.ui.preferences.navigation.AppDrawerFolder
import app.lawnchair.ui.util.bottomSheetHandler
import com.android.launcher3.R
import com.android.launcher3.model.data.FolderInfo
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val context = LocalContext.current
    val liveFolders by viewModel.folders.collectAsStateWithLifecycle()

    // FolderInfo doesn't implement structural equals(), and getFoldersFlow() maps fresh
    // instances on every emission - so liveFolders is a "new" list even when nothing actually
    // changed, e.g. right after this screen's own drag writes the new ranks and the DB flow
    // echoes them back. Feeding that straight into the reorderable list as its source of truth
    // made the just-dragged folder visibly snap back. Instead, track the displayed order
    // locally by id - initialized once, then only re-synced when a folder is actually added or
    // removed (not merely reordered) - and resolve each id against liveFolders at render time so
    // renames/content-count changes still show up live.
    var orderedIds by remember { mutableStateOf(liveFolders.map { it.id }) }
    LaunchedEffect(liveFolders) {
        val liveIds = liveFolders.map { it.id }
        if (liveIds.toSet() != orderedIds.toSet()) {
            orderedIds = orderedIds.filter { it in liveIds } + liveIds.filter { it !in orderedIds }
        }
    }
    val folders = remember(orderedIds, liveFolders) {
        val byId = liveFolders.associateBy { it.id }
        orderedIds.mapNotNull { byId[it] }
    }

    // Reordering only writes ranks to the DB; the live app drawer is refreshed once when the
    // user actually leaves this screen, same as app-level reordering inside a folder - reloading
    // on every single drag settle raced with the write and made the dragged folder snap back.
    var orderChanged by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose {
            if (orderChanged) viewModel.onFolderEditingFinished()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        viewModel.exportFolders { exportResult ->
            val writeResult = exportResult.mapCatching { json ->
                val stream = context.contentResolver.openOutputStream(uri)
                    ?: throw IOException("Unable to open output stream for $uri")
                stream.use { it.write(json.toByteArray()) }
            }
            val messageRes = if (writeResult.isSuccess) R.string.folder_export_success else R.string.folder_export_error
            Toast.makeText(context, messageRes, Toast.LENGTH_SHORT).show()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        val json = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: throw IOException("Unable to open input stream for $uri")
        }.getOrNull()
        if (json == null) {
            Toast.makeText(context, R.string.folder_import_error, Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        viewModel.importFolders(json) { importResult ->
            importResult
                .onSuccess { imported ->
                    val message = context.getString(
                        R.string.folder_import_success,
                        imported.importedFolders,
                        imported.importedApps,
                        imported.skippedApps,
                    )
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
                .onFailure {
                    Toast.makeText(context, R.string.folder_import_error, Toast.LENGTH_SHORT).show()
                }
        }
    }

    AppDrawerFoldersPreference(
        modifier = modifier,
        folders = folders,
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
        onReorderFolders = {
            orderedIds = it
            orderChanged = true
            viewModel.updateFolderOrder(it)
        },
        onExportFolders = {
            val fileName = "lawnchair_folders_${SimpleDateFormat.getDateTimeInstance().format(Date())}.json"
            Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json")
                .putExtra(Intent.EXTRA_TITLE, fileName)
                .let { exportLauncher.launch(it) }
        },
        onImportFolders = {
            Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json")
                .let { importLauncher.launch(it) }
        },
    )
}

@Composable
fun AppDrawerFoldersPreference(
    folders: List<FolderInfo>,
    onCreateFolder: (FolderInfo, String) -> Unit,
    onEditFolderItems: (Int) -> Unit,
    onRenameFolder: (FolderInfo, String) -> Unit,
    onDeleteFolder: (FolderInfo) -> Unit,
    onReorderFolders: (List<Int>) -> Unit,
    onExportFolders: () -> Unit,
    onImportFolders: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bottomSheetHandler = bottomSheetHandler
    val prefs = preferenceManager()
    val manualOrderAdapter = preferenceManager2().folderManualOrder.getAdapter()
    val manualOrder by manualOrderAdapter.state

    // folders already arrives ordered (rank in manual mode, alphabetical otherwise) - see
    // FolderService.getFoldersFlow().
    val alphabeticalOrder = remember(folders) {
        folders.sortedBy { it.title.toString().lowercase(Locale.getDefault()) }
    }

    PreferenceLayout(
        label = stringResource(id = R.string.app_drawer_folder),
        backArrowVisible = true,
        modifier = modifier,
        actions = {
            OverflowMenu {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.folder_export_action)) },
                    onClick = {
                        onExportFolders()
                        hideMenu()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.folder_import_action)) },
                    onClick = {
                        onImportFolders()
                        hideMenu()
                    },
                )
            }
        },
    ) {
        PreferenceGroup(
            heading = stringResource(R.string.settings),
        ) {
            SwitchPreference(
                adapter = prefs.folderApps.getAdapter(),
                label = stringResource(id = R.string.apps_in_folder_label),
                description = stringResource(id = R.string.apps_in_folder_description),
            )
            SwitchPreference(
                adapter = manualOrderAdapter,
                label = stringResource(id = R.string.folder_manual_order_label),
                description = stringResource(id = R.string.folder_manual_order_description),
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
                            onRename = onCreateFolder,
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
        if (folders.isNotEmpty()) {
            if (manualOrder) {
                ReorderablePreferenceGroup(
                    label = null,
                    items = folders,
                    defaultList = alphabeticalOrder,
                    onOrderChange = { onReorderFolders(it.map { folderInfo -> folderInfo.id }) },
                ) { folderInfo, _, _, onDraggingChange ->
                    FolderItem(
                        folderInfo = folderInfo,
                        onItemClick = {
                            bottomSheetHandler.show {
                                FolderEditSheet(
                                    folderInfo,
                                    onRename = onRenameFolder,
                                    onNavigate = {
                                        onEditFolderItems(it)
                                        bottomSheetHandler.hide()
                                    },
                                    onDismiss = {
                                        bottomSheetHandler.hide()
                                    },
                                )
                            }
                        },
                        onItemDelete = onDeleteFolder,
                        dragHandle = {
                            ReorderableDragHandle(
                                scope = this,
                                onDragStop = { onDraggingChange(false) },
                            )
                        },
                    )
                }
            } else {
                PreferenceGroup {
                    folders.forEach { folderInfo ->
                        FolderItem(
                            folderInfo = folderInfo,
                            onItemClick = {
                                bottomSheetHandler.show {
                                    FolderEditSheet(
                                        folderInfo,
                                        onRename = onRenameFolder,
                                        onNavigate = {
                                            onEditFolderItems(it)
                                            bottomSheetHandler.hide()
                                        },
                                        onDismiss = {
                                            bottomSheetHandler.hide()
                                        },
                                    )
                                }
                            },
                            onItemDelete = onDeleteFolder,
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
    onRename: (FolderInfo, String) -> Unit,
    onNavigate: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    hideAppPicker: Boolean = false,
) {
    val resources = LocalContext.current.resources
    var textFieldValue by remember { mutableStateOf(TextFieldValue(folderInfo.title.toString())) }

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
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    dragHandle: (@Composable () -> Unit)? = null,
) {
    val resources = LocalContext.current.resources
    PreferenceTemplate(
        title = {
            Text(
                text = folderInfo.title.toString(),
            )
        },
        description = {
            Text(
                text = resources.getQuantityString(R.plurals.apps_count, folderInfo.getContents().size, folderInfo.getContents().size),
            )
        },
        startWidget = dragHandle,
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
