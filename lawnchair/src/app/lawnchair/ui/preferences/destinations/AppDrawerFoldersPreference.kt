package app.lawnchair.ui.preferences.destinations

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import app.lawnchair.data.factory.ViewModelFactory
import app.lawnchair.data.folder.model.FolderViewModel
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.ModalBottomSheetContent
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.layout.LoadingScreen
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceGroupHeading
import app.lawnchair.ui.preferences.components.layout.PreferenceLayoutLazyColumn
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.ui.preferences.components.layout.preferenceGroupItems
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
    viewModel: FolderViewModel = viewModel(factory = ViewModelFactory(LocalContext.current) { FolderViewModel(it) }),
) {
    val navController = LocalNavController.current
    val folders by viewModel.folders.collectAsStateWithLifecycle()

    LaunchedEffect(folders) {
        viewModel.refreshFolders()
    }

    AppDrawerFoldersPreference(
        modifier = modifier,
        folders = folders,
        onCreateFolder = { folderInfo, label ->
            val newInfo = folderInfo.apply {
                title = label
            }
            viewModel.saveFolder(newInfo)
        },
        onEditFolderItems = {
            viewModel.setFolderInfo(it, false)
            navController.navigate(AppDrawerAppListToFolder(it))
        },
        onRenameFolder = { folderInfo, it ->
            folderInfo.apply {
                title = it
                viewModel.updateFolderInfo(this, false)
            }
        },
        onDeleteFolder = {
            viewModel.deleteFolder(it.id)
        },
        onRefreshList = {
            viewModel.refreshFolders()
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
    onRefreshList: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bottomSheetHandler = bottomSheetHandler
    val apps by appsState()

    LoadingScreen(
        isLoading = apps.isEmpty(),
        modifier = modifier.fillMaxWidth(),
    ) {
        PreferenceLayoutLazyColumn(
            label = stringResource(id = R.string.app_drawer_folder),
            backArrowVisible = true,
        ) {
            item(contentType = "heading") {
                PreferenceGroupHeading(stringResource(R.string.settings))
            }
            item {
                val prefs = preferenceManager()
                PreferenceGroup {
                    SwitchPreference(
                        adapter = prefs.folderApps.getAdapter(),
                        label = stringResource(id = R.string.apps_in_folder_label),
                        description = stringResource(id = R.string.apps_in_folder_description),
                    )
                }
            }
            item(contentType = "heading") {
                PreferenceGroupHeading(stringResource(R.string.folders_label))
            }
            item {
                PreferenceGroup {
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
            }
            preferenceGroupItems(
                folders,
                isFirstChild = true,
            ) { _, folderInfo ->
                FolderItem(
                    folderInfo,
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
                                    onRefreshList()
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
                        onItemDelete(folderInfo)
                    },
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        modifier = modifier.clickable {
            onItemClick(folderInfo)
        },
    )
}
