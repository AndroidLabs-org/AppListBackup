package org.androidlabs.applistbackup.docs

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewComposable(
    filename: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    val webView = remember {
        WebView(context).apply {
            // Required for the data-theme injection in onPageFinished below to run at
            // all — evaluateJavascript() against a WebView with JS disabled (the
            // default) silently does nothing, which is why the dark-theme fix here
            // didn't actually take effect despite the CSS being correct. Confirmed by
            // reproducing on a real emulator: the toolbar went dark (app-level Compose
            // theme) but the document body stayed white every time, with no way for
            // the page to have received the theme signal at all.
            settings.javaScriptEnabled = true

            // Let the page follow the device theme. Without this the WebView renders
            // light regardless, so these screens stayed white against a dark app bar.
            // The assets declare their own dark styling via prefers-color-scheme;
            // algorithmic darkening only covers the rest.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                settings.isAlgorithmicDarkeningAllowed = true
            }
            webViewClient = object : WebViewClient() {

                /**
                 * Keeps the app alive when the WebView's renderer process dies.
                 *
                 * The default implementation returns false, and the framework's documented
                 * response to false is to kill the process hosting the WebView — so a renderer
                 * crash took the whole app down without a word. That is not a rare event here:
                 * the renderer was observed crashing repeatedly on API 35 (`Renderer process
                 * crash detected`), and this app feeds it unusually large documents — an HTML
                 * backup embeds every icon as base64, which is what makes the files in issue
                 * #77 so big.
                 *
                 * A WebView whose renderer has gone cannot be reused, so it is detached and
                 * destroyed, and the user is told rather than left staring at a blank screen
                 * or a vanished app.
                 */
                override fun onRenderProcessGone(
                    view: WebView?,
                    detail: RenderProcessGoneDetail?
                ): Boolean {
                    view?.let { dead ->
                        (dead.parent as? ViewGroup)?.removeView(dead)
                        dead.destroy()
                    }
                    Toast.makeText(context, R.string.viewer_crashed, Toast.LENGTH_LONG).show()
                    return true
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val url = request?.url ?: return false

                    return try {
                        val intent = Intent(Intent.ACTION_VIEW, url)
                        context.startActivity(intent)
                        true
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(
                            context,
                            R.string.no_app_to_open_link,
                            Toast.LENGTH_SHORT
                        ).show()
                        true
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)

                    // prefers-color-scheme alone did not reliably reflect the app's
                    // theme in this WebView (issue found on real hardware) — the
                    // in-app report viewer works because it sets this explicitly, so
                    // this screen now does the same instead of relying on the media
                    // query alone.
                    val night = (configuration.uiMode and
                            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                    view?.evaluateJavascript(
                        "document.documentElement.dataset.theme = '${if (night) "dark" else "light"}';"
                    ) { }
                }
            }

            loadUrl("file:///android_asset/$filename.html")
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    AndroidView(
        factory = { webView },
        modifier = modifier.fillMaxSize()
    )
}
