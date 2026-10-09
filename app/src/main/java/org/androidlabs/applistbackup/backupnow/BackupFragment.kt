package org.androidlabs.applistbackup.backupnow

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.androidlabs.applistbackup.BackupService
import org.androidlabs.applistbackup.BackupTempStore
import org.androidlabs.applistbackup.MainActivityViewModel
import org.androidlabs.applistbackup.R
import org.androidlabs.applistbackup.data.BackupItem
import org.androidlabs.applistbackup.ui.theme.AppListBackupTheme
import kotlin.getValue

class BackupFragment : Fragment() {
    private val viewModel: BackupViewModel by viewModels()
    private val mainActivityViewModel: MainActivityViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                AppListBackupTheme {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        ActivityState(
                            modifier = Modifier.padding(innerPadding),
                            viewModel = viewModel,
                            mainViewModel = mainActivityViewModel,
                            runBackup = ::runBackup,
                            onRequestNotificationsPermission = ::onRequestNotificationsPermission
                        )
                    }
                }
            }
        }
    }

    private fun runBackup(temp:Boolean) {
        BackupService.run(requireContext(),
            temporary = temp,
            onComplete = { uri, temp ->
                mainActivityViewModel.navigateToBrowse()
            })
    }

    private fun onRequestNotificationsPermission() {
        val context = requireContext()
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        startActivity(intent)
    }
}

@Composable
private fun ActivityState(
    modifier: Modifier = Modifier,
    viewModel: BackupViewModel,
    mainViewModel: MainActivityViewModel,
    runBackup: (Boolean) -> Unit,
    onRequestNotificationsPermission: () -> Unit
) {
    val isNotificationEnabled = viewModel.notificationEnabled.observeAsState(initial = false)
    val backupUri = mainViewModel.backupUri.observeAsState()
    val backupFiles = mainViewModel.backupFiles.collectAsState(initial = emptyList())
    val isRunning by mainViewModel.isBackupRunning.collectAsState()

    LaunchedEffect(key1 = true) {
        viewModel.refreshNotificationStatus()
        mainViewModel.refreshBackupUri()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshNotificationStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.Center)
        ) {
            if (isNotificationEnabled.value != true) {
                Text(
                    text = stringResource(R.string.notifications_disabled),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onRequestNotificationsPermission) {
                    Text(text = stringResource(R.string.notifications_enable))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (backupUri.value != null) {

                Button(
                    onClick = { runBackup(true) },
                    enabled = !isRunning
                ) {
                    Text(text = stringResource(if (isRunning) R.string.in_progress else R.string.backup_temp))
                }

                Spacer(Modifier.height(18.dp))

                Button(
                    onClick = { runBackup(false) },
                    enabled = !isRunning
                ) {
                    Text(text = stringResource(if (isRunning) R.string.in_progress else R.string.backup_now))
                }

                if (backupFiles.value.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))

                    val lastBackup = backupFiles.value
                        .filterIsInstance<BackupItem.File>()
                        .firstOrNull()

                    if (lastBackup != null) {
                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = "${stringResource(R.string.last_backup)}: ${lastBackup.file.title}",
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.destination_not_set),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
