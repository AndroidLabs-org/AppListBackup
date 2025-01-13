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
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.androidlabs.applistbackup.BackupService
import org.androidlabs.applistbackup.R
import org.androidlabs.applistbackup.ui.theme.AppListBackupTheme

class BackupFragment : Fragment() {
    private val viewModel: BackupViewModel by viewModels()

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
                            runBackup = ::runBackup,
                        )
                    }
                }
            }
        }
    }

    private fun runBackup() {
        BackupService.run(requireContext())
    }
}

@Composable
private fun ActivityState(
    modifier: Modifier = Modifier,
    viewModel: BackupViewModel,
    runBackup: () -> Unit,
) {
    val isNotificationEnabled = viewModel.notificationEnabled.observeAsState(initial = false)
    val backupUri = viewModel.backupUri.observeAsState()
    val backupFiles = viewModel.backupFiles.observeAsState(initial = emptyList())

    LaunchedEffect(key1 = true) {
        viewModel.refreshNotificationStatus()
        viewModel.refreshBackupUri()
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

    val context = LocalContext.current
    val settingsIntent = remember {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
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
                Button(onClick = { context.startActivity(settingsIntent) }) {
                    Text(text = stringResource(R.string.notifications_enable))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (backupUri.value != null) {
                Button(onClick = runBackup) {
                    Text(text = stringResource(R.string.backup_now))
                }

                if (backupFiles.value.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = "${stringResource(R.string.last_backup)}: ${backupFiles.value.first().title}",
                        fontSize = 12.sp
                    )
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
