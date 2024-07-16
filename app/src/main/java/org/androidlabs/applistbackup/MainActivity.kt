package org.androidlabs.applistbackup

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.androidlabs.applistbackup.docs.DocsViewerActivity
import org.androidlabs.applistbackup.faq.InstructionsActivity
import org.androidlabs.applistbackup.reader.BackupReaderActivity
import org.androidlabs.applistbackup.ui.theme.AppListBackupTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainActivityViewModel by viewModels()
    private lateinit var openFolderLauncher: ActivityResultLauncher<Intent>
    private lateinit var setFolderLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appName = packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()

        openFolderLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            Log.d("SettingsFragment", "Open folder result: $result")
        }
        setFolderLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { uri ->
                    val takeFlags = result.data?.flags?.and(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    if (takeFlags != null) {
                        contentResolver.takePersistableUriPermission(uri, takeFlags)
                    }

                    viewModel.saveBackupUri(uri)
                }
            }
        }

        if (intent?.extras?.getBoolean("RUN_BACKUP") == true) {
            runBackup()
        }

        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val version = "${packageInfo.versionName} (${packageInfo.longVersionCode})"

        enableEdgeToEdge()
        setContent {
            AppListBackupTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ActivityState(
                        modifier = Modifier.padding(innerPadding),
                        viewModel = viewModel,
                        openLastBackup = ::openLastBackup,
                        runBackup = ::runBackup,
                        faq = ::faq,
                        selectBackupFolder = ::selectBackupFolder,
                        version = version,
                        appName = appName,
                        openDoc = ::openDoc
                    )
                }
            }
        }
    }

    private fun openLastBackup() {
        val lastBackupUri = BackupService.getLastCreatedFileUri(this)
        val intent = Intent(this, BackupReaderActivity::class.java)
        if (lastBackupUri != null) {
            intent.putExtra("uri", lastBackupUri.toString())
        }
        startActivity(intent)
    }

    private fun runBackup() {
        BackupService.run(this)
    }

    private fun faq() {
        val intent = Intent(this, InstructionsActivity::class.java)
        startActivity(intent)
    }

    private fun selectBackupFolder() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        setFolderLauncher.launch(intent)
    }

    private fun openDoc(name: String, fileName: String) {
        val intent = Intent(this, DocsViewerActivity::class.java).apply {
            putExtra("name", name)
            putExtra("filename", fileName)
        }
        startActivity(intent)
    }
}

@Composable
fun ActivityState(
    modifier: Modifier = Modifier,
    viewModel: MainActivityViewModel,
    openLastBackup: () -> Unit,
    runBackup: () -> Unit,
    faq: () -> Unit,
    selectBackupFolder: () -> Unit,
    version: String,
    appName: String,
    openDoc: (name: String, fileName: String) -> Unit
) {
    val isNotificationEnabled = viewModel.notificationEnabled.observeAsState(initial = false)
    val backupUri = viewModel.backupUri.observeAsState()

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
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth(),
        ) {
            Icon(
                painter = painterResource(id = R.mipmap.ic_launcher),
                contentDescription = "Icon",
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = appName,
                fontSize = 18.sp
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.Center)
        ) {
            if (isNotificationEnabled.value != true) {
                Text(
                    text = "Notifications are disabled.\nEnable notifications for correct functionality.",
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { context.startActivity(settingsIntent) }) {
                    Text(text = "Enable Notifications")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (backupUri.value != null) {
                Button(onClick = runBackup) {
                    Text(text = "Run Backup")
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(onClick = openLastBackup) {
                    Text(text = "View backup")
                }
            } else {
                Text(
                    text = "Please select a backup folder\nto begin using the app.",
                    textAlign = TextAlign.Center
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Button(onClick = selectBackupFolder) {
                Text(text = if (backupUri.value == null) "Choose backup destination" else "Change backup destination")
            }

            if (backupUri.value !== null) {
                Text(
                    text = "Current: ${BackupService.getReadablePathFromUri(backupUri.value)}",
                    fontSize = 12.sp,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = faq) {
                Text(text = "FAQ")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Version: $version",
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            ClickableText(
                text = AnnotatedString("Terms and Conditions"),
                style = TextStyle(fontSize = 12.sp, color = MaterialTheme.colorScheme.primary),
                onClick = {
                    openDoc("Terms and Conditions", "terms")
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            ClickableText(
                text = AnnotatedString("Privacy Policy"),
                style = TextStyle(fontSize = 12.sp, color = MaterialTheme.colorScheme.primary),
                onClick = {
                    openDoc("Privacy Policy", "privacy")
                }
            )
        }
    }
}
