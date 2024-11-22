package org.androidlabs.applistbackup.reader

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import org.androidlabs.applistbackup.BackupFile
import org.androidlabs.applistbackup.BackupService
import org.androidlabs.applistbackup.R
import org.androidlabs.applistbackup.ui.theme.AppListBackupTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class BackupReaderActivity : ComponentActivity() {
    private lateinit var viewModel: BackupViewModel

    private val pickHtmlFile =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let {
                val contentResolver = contentResolver
                val fileName = contentResolver.query(it, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    cursor.moveToFirst()
                    cursor.getString(nameIndex)
                }

                if (fileName != null && fileName.endsWith(".html") && fileName.contains("app-list-backup")) {
                    try {
                        val tempFile = File(cacheDir, fileName)
                        contentResolver.openInputStream(it)?.use { inputStream ->
                            tempFile.outputStream().use { outputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                        val tempFileUri = FileProvider.getUriForFile(this, "$packageName.provider", tempFile)
                        viewModel.setUri(tempFileUri)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(this, getString(R.string.error_message, e.message), Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(
                        this,
                        getString(R.string.wrong_file),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory(application)
        )[BackupViewModel::class.java]

        val uriString = intent.getStringExtra("uri")

        if (uriString != null) {
            viewModel.setUri(Uri.parse(uriString))
        }

        setContent {
            AppListBackupTheme {
                BackupScreen(
                    viewModel = viewModel,
                    onBack = {
                        onBackPressedDispatcher.onBackPressed()
                    },
                    onBrowse = {
                        pickHtmlFile.launch(arrayOf("text/html"))
                    },
                    runBackup = ::runBackup
                )
            }
        }
    }

    private fun runBackup() {
        if (viewModel.uri.value == null) {
            BackupService.run(this, onComplete = { uri ->
                viewModel.setUri(uri)
            })
        } else {
            BackupService.run(this)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    viewModel: BackupViewModel,
    onBack: () -> Unit,
    onBrowse: () -> Unit,
    runBackup: () -> Unit
) {
    val uri by viewModel.uri.observeAsState()
    val backups by viewModel.backupFiles.observeAsState(initial = emptyList())
    val installedPackages by viewModel.installedPackages.observeAsState(initial = emptyList())

    fun onSelect(uri: Uri) {
        viewModel.setUri(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.backup)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onBrowse) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_browse),
                            contentDescription = stringResource(R.string.browse),
                        )
                    }
                }
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        DisplayHtmlContent(uri, installedPackages = installedPackages, backups, onSelect = ::onSelect, runBackup, Modifier.padding(innerPadding))
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DisplayHtmlContent(
    uri: Uri?,
    installedPackages: List<String>,
    backups: List<BackupFile>,
    onSelect: (uri: Uri) -> Unit,
    runBackup: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    val titleFormatter = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

    val context = LocalContext.current
    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    request?.url?.let { url ->
                        val intent = Intent(Intent.ACTION_VIEW, url)
                        context.startActivity(intent)
                        return true
                    }
                    return false
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    val packagesList = installedPackages.joinToString(",") { "\"$it\"" }
                    view?.evaluateJavascript("setInstalledApps([$packagesList])")  { }
                }
            }
        }
    }

    LaunchedEffect(uri) {
        uri?.let {
            webView.loadUrl(it.toString())
        }
    }

    if (uri != null) {
        Column(modifier = modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 16.dp, end = 4.dp)
            ) {
                Text(
                    text = BackupService.parseDateFromUri(uri)?.let { titleFormatter.format(it) } ?: stringResource(R.string.backup),
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
                        onDismissRequest = { expanded = false }
                    ) {
                        backups.forEach { backup ->
                            DropdownMenuItem(text = {
                                Text(backup.title)
                            }, onClick = {
                                expanded = false
                                onSelect(backup.uri)
                            })
                        }
                    }
                }
            }

            AndroidView(factory = { webView }, modifier = Modifier.weight(1f))
        }
    } else {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = stringResource(R.string.no_backup_found))

                Spacer(modifier = Modifier.height(16.dp))

                Button(onClick = runBackup) {
                    Text(text = stringResource(R.string.run_backup))
                }
            }
        }
    }
}

