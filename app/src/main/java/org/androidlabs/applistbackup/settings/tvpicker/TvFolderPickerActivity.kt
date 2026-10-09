package org.androidlabs.applistbackup.settings.tvpicker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.androidlabs.applistbackup.R
import org.androidlabs.applistbackup.utils.Utils.clearPrefixSlash
import java.io.File

class TvFolderPickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            TvFolderPicker(
                onFolderSelected = { selectedFolder ->
                    setResult(RESULT_OK, Intent().apply {
                        data = Uri.fromFile(selectedFolder)
                    })
                    finish()
                },
                onCancel = {
                    finish()
                }
            )
        }
    }
}

@Composable
private fun TvFolderPicker(
    onFolderSelected: (File) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val externalStorage = Environment.getExternalStorageDirectory()
    val externalStoragePath = externalStorage.path
    var currentPath by remember { mutableStateOf(externalStorage) }
    var folders by remember { mutableStateOf<List<File>>(emptyList()) }
    var selectedIndex by remember { mutableIntStateOf(0) }
    var showNewFolderDialog by remember { mutableStateOf(false) }

    val isExternalStorage = currentPath == externalStorage
    val startItemsCount = if (isExternalStorage) 2 else 3

    val prefix = "Internal Storage"

    val formattedPath = if (isExternalStorage) prefix else "$prefix/${
        clearPrefixSlash(
            currentPath.path.removePrefix(
                externalStoragePath
            )
        )
    }"

    val listState = rememberLazyListState()

    val totalItems = folders.size + startItemsCount

    LaunchedEffect(currentPath) {
        selectedIndex = 0
        folders = currentPath.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedBy { it.name }
            ?.toList()
            ?: emptyList()
    }

    LaunchedEffect(selectedIndex) {
        listState.animateScrollToItem(selectedIndex)
    }

    BackHandler {
        if (!isExternalStorage) {
            currentPath = currentPath.parentFile ?: currentPath
        } else {
            onCancel()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
            .focusable(true)
            .onKeyEvent { keyEvent ->
                when {
                    keyEvent.key == Key.DirectionDown &&
                            keyEvent.type == KeyEventType.KeyDown -> {
                        selectedIndex = (selectedIndex + 1) % totalItems
                        true
                    }

                    keyEvent.key == Key.DirectionUp &&
                            keyEvent.type == KeyEventType.KeyDown -> {
                        selectedIndex = if (selectedIndex > 0) {
                            selectedIndex - 1
                        } else {
                            totalItems - 1
                        }
                        true
                    }

                    // A television remote's OK button sends DPAD_CENTER, not ENTER. Only
                    // Key.Enter was accepted here, so on an actual remote nothing in this
                    // screen could be chosen at all — and this screen is the only way to pick
                    // a backup folder on TV, there being no Storage Access Framework picker.
                    // Verified on the TV emulator: DPAD_CENTER did nothing where ENTER
                    // navigated. NumPadEnter is accepted too, for keyboard-attached devices.
                    (keyEvent.key == Key.Enter ||
                            keyEvent.key == Key.DirectionCenter ||
                            keyEvent.key == Key.NumPadEnter) &&
                            keyEvent.type == KeyEventType.KeyDown -> {
                        // Index 2 is "Go to Parent Folder" only when there is a parent to go
                        // to. At the storage root there is not, so startItemsCount is 2 and
                        // index 2 is the *first folder in the list* — which the old `2 ->`
                        // branch swallowed, leaving it impossible to open. Alphabetically
                        // that is usually "Alarms"; on the emulator it is exactly that.
                        when {
                            selectedIndex == 0 -> onFolderSelected(currentPath)
                            selectedIndex == 1 -> showNewFolderDialog = true
                            selectedIndex == 2 && !isExternalStorage ->
                                currentPath = currentPath.parentFile ?: currentPath

                            else -> {
                                val folderIndex = selectedIndex - startItemsCount
                                if (folderIndex in folders.indices) {
                                    currentPath = folders[folderIndex]
                                }
                            }
                        }
                        true
                    }

                    else -> false
                }
            }
    ) {
        Text(
            text = stringResource(R.string.tv_select_folder),
            style = MaterialTheme.typography.headlineLarge,
            color = Color.White,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Text(
            text = stringResource(R.string.tv_current_folder, formattedPath),
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyColumn(
            state = listState
        ) {
            item {
                FolderPickerItem(
                    text = stringResource(R.string.tv_select_this_folder),
                    isFocused = selectedIndex == 0,
                    onClick = { onFolderSelected(currentPath) },
                )
            }

            item {
                FolderPickerItem(
                    text = stringResource(R.string.tv_create_new_folder),
                    isFocused = selectedIndex == 1,
                    onClick = { showNewFolderDialog = true }
                )
            }

            if (!isExternalStorage) {
                currentPath.parentFile?.let { parent ->
                    item {
                        FolderPickerItem(
                            text = stringResource(R.string.tv_go_to_parent_folder),
                            isFocused = selectedIndex == 2,
                            onClick = { currentPath = parent }
                        )
                    }
                }
            }

            items(folders.count()) { index ->
                val folder = folders[index]

                FolderPickerItem(
                    text = stringResource(R.string.tv_folder_item, folder.name),
                    isFocused = index == selectedIndex - startItemsCount,
                    onClick = { currentPath = folder }
                )
            }
        }
    }

    if (showNewFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = {
                Text(
                    stringResource(R.string.tv_create_new_folder_title),
                    color = Color.White
                )
            },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text(stringResource(R.string.tv_folder_name), color = Color.White) },
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 16.sp
                    ),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (folderName.isNotEmpty()) {
                            val newFolder = File(currentPath, folderName)
                            if (newFolder.mkdir()) {
                                folders = currentPath.listFiles()
                                    ?.filter { it.isDirectory }
                                    ?.sortedBy { it.name }
                                    ?.toList()
                                    ?: emptyList()
                            }
                        }
                        showNewFolderDialog = false
                    }
                ) {
                    Text(stringResource(R.string.tv_create), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) {
                    Text(stringResource(R.string.tv_cancel), color = Color.White)
                }
            },
            containerColor = Color.DarkGray
        )
    }
}

@Composable
private fun FolderPickerItem(
    text: String,
    isFocused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isFocused) Color(0xFF0E4DA4)
                else Color.Transparent
            )
            .clickable { onClick() }
    ) {
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp)
        )
    }
}