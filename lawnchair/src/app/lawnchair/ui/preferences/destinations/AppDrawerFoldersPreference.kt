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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileMove
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
import androidx.compose.runtime.collectAsState
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
import app.lawnchair.data.folder.model.NestableFolder
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.ModalBottomSheetContent
import app.lawnchair.ui.OverflowMenu
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.ui.preferences.navigation.AppDrawerAppListToFolder
import app.lawnchair.ui.preferences.navigation.AppDrawerFolder
import app.lawnchair.ui.util.bottomSheetHandler
import com.android.launcher3.R
import com.android.launcher3.model.data.FolderInfo
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.Flow

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
    val folders by viewModel.folders.collectAsStateWithLifecycle()

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
        onNestFolder = { folderInfo ->
            bottomSheetHandler.show {
                NestFolderSheet(
                    nestableFolders = viewModel.nestableFolders(folderInfo.id),
                    onSelect = { parentId ->
                        viewModel.setParentFolder(folderInfo.id, parentId)
                        bottomSheetHandler.hide()
                    },
                    onDismiss = { bottomSheetHandler.hide() },
                )
            }
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
    onNestFolder: (FolderInfo) -> Unit,
    onExportFolders: () -> Unit,
    onImportFolders: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bottomSheetHandler = bottomSheetHandler
    val prefs = preferenceManager()

    // Folders are sorted alphabetically on display; no manual order is stored.
    val sortedDisplayList = remember(folders) {
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
        if (sortedDisplayList.isNotEmpty()) {
            PreferenceGroup {
                sortedDisplayList.forEach { folderInfo ->
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
                        onItemNest = onNestFolder,
                    )
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
    onItemNest: (FolderInfo) -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
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
        endWidget = {
            Row {
                IconButton(
                    onClick = {
                        onItemNest(folderInfo)
                    },
                ) {
                    Icon(Icons.Rounded.DriveFileMove, contentDescription = stringResource(R.string.folder_nest_action), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
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

@Composable
fun NestFolderSheet(
    nestableFolders: Flow<List<NestableFolder>>,
    onSelect: (Int?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val folders by nestableFolders.collectAsState(initial = emptyList())

    ModalBottomSheetContent(
        buttons = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        modifier = modifier,
    ) {
        LazyColumn {
            item {
                ClickablePreference(
                    label = stringResource(R.string.folder_nest_top_level),
                    modifier = Modifier.padding(horizontal = 8.dp),
                    onClick = { onSelect(null) },
                )
            }
            items(folders) { folder ->
                ClickablePreference(
                    label = folder.title,
                    modifier = Modifier.padding(horizontal = 8.dp),
                    onClick = { onSelect(folder.id) },
                )
            }
        }
    }
}
