package org.androidlabs.applistbackup.reader

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.androidlabs.applistbackup.BackupService
import org.androidlabs.applistbackup.MainActivityViewModel
import org.androidlabs.applistbackup.R
import java.text.SimpleDateFormat
import java.util.Locale

class BackupReaderFragment(
    private val mainActivityViewModel: MainActivityViewModel
) : Fragment() {
    private val viewModel: BackupViewModel by viewModels()

    private var uriSubscription: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val lastUri = BackupService.getLastCreatedFileUri(requireContext())
        lastUri?.let {
            viewModel.setUri(it)
        }
    }

    override fun onDestroy() {
        uriSubscription?.cancel()
        uriSubscription = null
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainActivityViewModel.uri.collect { uri ->
                    uri?.let {
                        viewModel.setUri(it)
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
                DisplayHtmlContent(
                    viewModel = viewModel,
                    runBackup = ::runBackup
                )
            }
        }
    }

    private fun runBackup() {
        val context = requireContext()
        if (viewModel.uri.value == null) {
            BackupService.run(context, onComplete = { uri ->
                viewModel.setUri(uri)
            })
        } else {
            BackupService.run(context)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun DisplayHtmlContent(
    viewModel: BackupViewModel,
    runBackup: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    val uri by viewModel.uri.observeAsState()
    val backups by viewModel.backupFiles.observeAsState(initial = emptyList())
    val installedPackages by viewModel.installedPackages.observeAsState(initial = emptyList())

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
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 16.dp, end = 4.dp)
            ) {
                Text(
                    text = BackupService.parseDateFromUri(uri!!)?.let { titleFormatter.format(it) } ?: stringResource(R.string.backup),
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
                                viewModel.setUri(backup.uri)
                            })
                        }
                    }
                }
            }

            AndroidView(factory = { webView }, modifier = Modifier.weight(1f))
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

