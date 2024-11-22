package org.androidlabs.applistbackup.docs

import android.content.Intent
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.androidlabs.applistbackup.R
import org.androidlabs.applistbackup.ui.theme.AppListBackupTheme

class DocsViewerActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val name = intent.getStringExtra("name") ?: return
        val filename = intent.getStringExtra("filename") ?: return

        setContent {
            AppListBackupTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(name) },
                            navigationIcon = {
                                IconButton(onClick = { onBackPressedDispatcher.onBackPressed() }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = getString(R.string.back)
                                    )
                                }
                            },
                        )
                    },
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    WebViewComposable(filename, Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun WebViewComposable(filename: String, modifier: Modifier = Modifier) {
    AndroidView(factory = {
        WebView(it).apply {
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    request?.url?.let { url ->
                        val intent = Intent(Intent.ACTION_VIEW, url)
                        context.startActivity(intent)
                        return true
                    }
                    return false
                }
            }
            loadUrl("file:///android_asset/$filename.html")
        }
    }, modifier = modifier.fillMaxSize())
}
