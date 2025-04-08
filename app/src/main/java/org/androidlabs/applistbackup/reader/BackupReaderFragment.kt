package org.androidlabs.applistbackup.reader

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.androidlabs.applistbackup.BackupService
import org.androidlabs.applistbackup.MainActivityViewModel
import org.androidlabs.applistbackup.R
import org.androidlabs.applistbackup.data.BackupFormat

class BackupReaderFragment(
    private val mainActivityViewModel: MainActivityViewModel
) : Fragment() {
    private val viewModel: BackupViewModel by viewModels()

    private var uriSubscription: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadLastBackup()
    }

    override fun onDestroy() {
        uriSubscription?.cancel()
        uriSubscription = null
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
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
                        loadLastBackup()
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
                    viewModel = viewModel,
                    runBackup = ::runBackup
                )
            }
        }
    }


    private fun loadLastBackup() {
        viewModel.viewModelScope.launch(Dispatchers.IO) {
            val lastUri = BackupService.getLastCreatedFileUri(requireContext())
            lastUri?.let {
                withContext(Dispatchers.Main) {
                    viewModel.setUri(requireContext(), it)
                }
            }
        }
    }

    private fun runBackup() {
        val context = requireContext()
        if (viewModel.uri.value == null) {
            BackupService.run(context, onComplete = { uri ->
                viewModel.setUri(context, uri)
            })
        } else {
            BackupService.run(context)
        }
    }
}

@Composable
private fun DisplayContent(
    viewModel: BackupViewModel,
    runBackup: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp

    val uri by viewModel.uri.collectAsState()
    val backups by viewModel.backupFiles.collectAsState(initial = emptyList())
    val installedPackages by viewModel.installedPackages.collectAsState(initial = emptyList())

    if (uri != null) {
        val extension = uri.toString().substringAfterLast('.', "").lowercase()
        val format = BackupFormat.fromExtension(extension)

        val uriBackup = remember(uri, backups) {
            backups.find { backup -> backup.uri == uri }
        }

        val title = uriBackup?.titleWithGeneration()
            ?: (BackupService.getFileInfoFromUri(context, uri!!)?.name
                ?: stringResource(R.string.backup))

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 16.dp, end = 4.dp)
            ) {
                Text(
                    text = title,
                    modifier = Modifier
                        .weight(1f)
                )

                Box {
                    IconButton(onClick = { expanded = true }) {
                        Icon(
                            Icons.Filled.ArrowDropDown,
                            contentDescription = stringResource(R.string.back)
                        )
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier
                            .heightIn(max = screenHeight * 0.75f)
                    ) {
                        backups.forEach { backup ->
                            DropdownMenuItem(text = {
                                Text(backup.titleWithGeneration())
                            }, onClick = {
                                expanded = false
                                viewModel.setUri(context, backup.uri)
                            })
                        }
                    }
                }
            }

            when (format) {
                BackupFormat.HTML -> {
                    BackupWebView(
                        modifier = Modifier.weight(1f),
                        uri = uri,
                        installedPackages = installedPackages
                    )
                }

                BackupFormat.CSV -> {
                    BackupCsvView(
                        modifier = Modifier.weight(1f),
                        uri = uri
                    )
                }

                BackupFormat.Markdown -> {
                    BackupMarkdownView(
                        modifier = Modifier
                            .weight(1f)
                            .padding(16.dp), uri = uri
                    )
                }
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = stringResource(R.string.no_backup_found))

                Spacer(modifier = Modifier.height(16.dp))

                Button(onClick = runBackup) {
                    Text(text = stringResource(R.string.backup_now))
                }
            }
        }
    }
}
