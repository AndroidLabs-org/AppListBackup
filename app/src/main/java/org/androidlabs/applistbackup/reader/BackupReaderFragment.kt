package org.androidlabs.applistbackup.reader

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.androidlabs.applistbackup.BackupService
import org.androidlabs.applistbackup.BackupTempStore
import org.androidlabs.applistbackup.MainActivityViewModel
import org.androidlabs.applistbackup.R
import org.androidlabs.applistbackup.data.BackupFormat
import org.androidlabs.applistbackup.data.BackupItem

class BackupReaderFragment() : Fragment() {
    private val viewModel: BackupReaderViewModel by viewModels()
    private val mainActivityViewModel: MainActivityViewModel by activityViewModels()


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    mainActivityViewModel.uri.collect { uri ->
                        uri?.let {
                            viewModel.setUri(requireContext(), it)
                        }
                    }
                }

                launch {
                    BackupService.isRunning.collect { running ->
                        if (!running) {
                            context?.let { ctx ->
                                viewModel.loadLastBackup(ctx)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                DisplayContent(
                    mainActivityViewModel = mainActivityViewModel,
                    viewModel = viewModel,
                    runBackup = ::runBackup,
                    onBackupDeleted = {
                        context?.let { ctx->
                            viewModel.loadLastBackup(ctx)
                        }
                    }
                )
            }
        }
    }


    private fun runBackup() {
        val context = requireContext()
        BackupService.run(context, temporary = false, onComplete = { uri, temp ->
            mainActivityViewModel.setUri(uri)
            viewModel.setUri(context, uri)
            mainActivityViewModel.navigateToBrowse()
        })
    }
}

@Composable
private fun DisplayContent(
    mainActivityViewModel: MainActivityViewModel,
    viewModel: BackupReaderViewModel,
    runBackup: () -> Unit,
    onBackupDeleted: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val windowInfo = LocalWindowInfo.current
    val screenHeight = with(LocalDensity.current) {
        windowInfo.containerSize.height.toDp()
    }
    val uri by viewModel.uri.collectAsState()
    val backups by mainActivityViewModel.backupFiles.collectAsState(initial = emptyList())
    val installedPackages by viewModel.installedPackages.collectAsState(initial = emptyList())
    val tempBackups by BackupTempStore.data.collectAsState()
    val selectedTemp by viewModel.selectedTemp.collectAsState()
    val isLoadingBackup by viewModel.isLoadingBackup.collectAsState()
    val activeScreen by mainActivityViewModel.activeScreen.collectAsState()


    val isBackupRunning by mainActivityViewModel.isBackupRunning.collectAsState()

    LaunchedEffect(uri) {
        mainActivityViewModel.setUri(uri)
    }

    LaunchedEffect(selectedTemp) {
        mainActivityViewModel.setSelectedTempFormat(selectedTemp?.format)
    }

    LaunchedEffect(activeScreen) {
        if (activeScreen == MainActivityViewModel.ActiveScreen.READER) {
            viewModel.resetSelection()
            viewModel.loadLastBackup(context)
        }
    }
    val dropdownItems = remember(backups, tempBackups) {
        val tempItems = tempBackups.map { (format, content) ->
            BackupItem.Temp(format, content)
        }
        tempItems.ifEmpty { backups }
    }

    LaunchedEffect(Unit) {
        mainActivityViewModel.backupDeletedEvent.collect {
            onBackupDeleted()
        }
    }

    if (isLoadingBackup) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }
    val isTempMode = selectedTemp != null

    if (dropdownItems.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = stringResource(R.string.no_backup_found))
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = runBackup, enabled = !isBackupRunning) {
                    Text(text = stringResource(if (isBackupRunning) R.string.in_progress else R.string.backup_now))
                }
            }
        }
        return
    }
    val activeUri = if (selectedTemp != null) null else uri

    if (activeUri != null || selectedTemp!=null) {

        val format = if (isTempMode) {
            selectedTemp!!.format
        } else {
            val fileName = DocumentFile.fromSingleUri(context, activeUri!!)?.name

            val extension = fileName
                ?.substringAfterLast('.', "")
                ?.lowercase()

            extension?.let { BackupFormat.fromExtension(it) }
        }

        if (format == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.unsupported_file_format))
            }
            return
        }

        val uriBackup = remember(uri, backups) {
            backups.firstOrNull {
                it is BackupItem.File && it.file.uri == uri
            } as? BackupItem.File
        }

        val title = selectedTemp?.format?.toString()
            ?: (uriBackup?.file?.titleWithGeneration()
                ?: (BackupService.getFileInfoFromUri(context, uri!!)?.name
                    ?: stringResource(R.string.backup)))

        Column(modifier = Modifier.fillMaxSize()) {
            val selectBackupLabel = stringResource(R.string.select_backup)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(start = 16.dp, end = 4.dp)
                    .clickable { expanded = true }
                    // The value Text and the dropdown-arrow Icon used to be two
                    // separate focus stops: TalkBack reached the date, then
                    // reached the icon — which on real hardware read the date a
                    // second time and never spoke "Select a backup" at all. One
                    // merged, named control matches how the rest of the app's
                    // rows behave.
                    .semantics(mergeDescendants = true) {
                        contentDescription = "$selectBackupLabel, $title"
                    }
            ) {
                Text(
                    text = title,
                    modifier = Modifier
                        .weight(1f)
                        .clearAndSetSemantics {}
                )

                Box {
                    IconButton(
                        onClick = { expanded = true },
                        modifier = Modifier.clearAndSetSemantics {}
                    ) {
                        Icon(
                            Icons.Filled.ArrowDropDown,
                            contentDescription = null
                        )
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier
                            .zIndex(2f)
                            .heightIn(max = screenHeight * 0.75f)
                    ) {
                        dropdownItems.forEach { item ->
                            when(item) {
                               is BackupItem.File -> {
                                   val itemTitle = item.file.titleWithGeneration()
                                   DropdownMenuItem(modifier = Modifier.semantics {
                                       contentDescription = itemTitle
                                   }, text = {
                                       Text(itemTitle)
                                   }, onClick = {
                                       expanded = false
                                       viewModel.selectTemp(null)
                                       mainActivityViewModel.setUri(item.file.uri)
                                       viewModel.setUri(context, item.file.uri)
                                   })
                               }
                               is BackupItem.Temp -> {
                                   val tempTitle = item.format.value
                                   DropdownMenuItem(modifier = Modifier.semantics {
                                       contentDescription = tempTitle
                                   }, text = {
                                       Text(tempTitle)
                                   }, onClick = {
                                       expanded = false
                                       mainActivityViewModel.setUri(null)
                                       viewModel.setUri(context, null)
                                       viewModel.selectTemp(item)
                                   })
                               }
                            }
                        }
                    }
                }
            }

            BackupWebView(
                mainActivityViewModel = mainActivityViewModel,
                viewModel = viewModel,
                modifier = Modifier.weight(1f),
                uri = activeUri,
                installedPackages = installedPackages,
                backupFormat = format,
                tempContent = selectedTemp?.content
            )
        }
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = stringResource(R.string.no_backup_found))

                Spacer(modifier = Modifier.height(16.dp))

                Button(onClick = runBackup, enabled = !isBackupRunning) {
                    Text(text = stringResource(if (isBackupRunning) R.string.in_progress else R.string.backup_now))
                }
            }
        }
    }
}
