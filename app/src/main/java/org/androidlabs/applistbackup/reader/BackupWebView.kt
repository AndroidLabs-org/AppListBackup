package org.androidlabs.applistbackup.reader

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView


@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BackupWebView(
    modifier: Modifier = Modifier,
    uri: Uri?,
    installedPackages: List<String>
) {
    val context = LocalContext.current
    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
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
                    view?.evaluateJavascript("setInstalledApps([$packagesList])") { }
                }
            }
        }
    }

    LaunchedEffect(uri) {
        uri?.let {
            webView.loadUrl(it.toString())
        }
    }

    AndroidView(
        factory = { webView },
        modifier = modifier
    )
}