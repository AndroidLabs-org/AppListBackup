package org.androidlabs.applistbackup.reader

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BackupWebView(
    modifier: Modifier = Modifier,
    uri: Uri?,
    installedPackages: List<String>
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val packagesList = remember(installedPackages) {
        installedPackages.joinToString(",") { "\"$it\"" }
    }

    val webView = remember {
        WebView(context).apply {
            post {
                settings.apply {
                    javaScriptEnabled = true

                    cacheMode = WebSettings.LOAD_NO_CACHE
                    domStorageEnabled = true

                    blockNetworkImage = false
                    loadsImagesAutomatically = true

                    setLayerType(View.LAYER_TYPE_HARDWARE, null)
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        isLoading = true
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        request?.url?.let { url ->
                            coroutineScope.launch(Dispatchers.IO) {
                                val intent = Intent(Intent.ACTION_VIEW, url)
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            }
                            return true
                        }
                        return false
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        isLoading = false

                        coroutineScope.launch(Dispatchers.Default) {
                            val script = "setInstalledApps([$packagesList])"
                            withContext(Dispatchers.Main) {
                                view?.evaluateJavascript(script) { }
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(uri) {
        if (uri != null) {
            withContext(Dispatchers.IO) {
                val urlString = uri.toString()
                withContext(Dispatchers.Main) {
                    isLoading = true
                    webView.loadUrl(urlString)
                }
            }
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize()
        )

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView.apply {
                stopLoading()
                coroutineScope.launch(Dispatchers.Main) {
                    destroy()
                }
            }
        }
    }
}