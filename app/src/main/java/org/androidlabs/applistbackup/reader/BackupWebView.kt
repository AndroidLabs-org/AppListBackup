package org.androidlabs.applistbackup.reader

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.androidlabs.applistbackup.MainActivityViewModel
import org.androidlabs.applistbackup.R
import org.androidlabs.applistbackup.data.BackupFormat
import org.androidlabs.applistbackup.utils.Utils.toCssHex

/** View tag key used to remember which content hash is currently loaded in the WebView. */
private const val CONTENT_HASH_TAG = 123456789

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BackupWebView(
    mainActivityViewModel: MainActivityViewModel,
    viewModel: BackupReaderViewModel,
    modifier: Modifier = Modifier,
    backupFormat: BackupFormat,
    uri: Uri?,
    installedPackages: List<String>,
    tempContent: String? = null
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val convertState by viewModel.convertState.collectAsState()
    val packagesList = remember(installedPackages) {
        installedPackages.joinToString(",") { "\"$it\"" }
    }
    val textColor = MaterialTheme.colorScheme.onSurface.toCssHex()
    val linkColor = MaterialTheme.colorScheme.primary.toCssHex()
    val codeBg = MaterialTheme.colorScheme.surface.toCssHex()
    val bgColor = MaterialTheme.colorScheme.background.toCssHex()
    val contentKey = if (tempContent != null) {
        "temp:$backupFormat:${tempContent.length}:${tempContent.firstOrNull()}"
    } else {
        "file:$backupFormat:$uri"
    }

    val webView = remember { //(contentKey) {
        WebView(context).apply {

            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            // Found on real hardware: TalkBack could not reach the report's content at
            // all (issue F6) — the WebView showed up in the tree with no children, so a
            // screen-reader user could open a backup but never read what's in it.
            // Explicit, since something in this view's ancestry may otherwise leave it
            // ambiguous. This alone is not confirmed to fix it — the more likely culprit
            // is the graphicsLayer { alpha = 0.99f } compositing trick a few lines below
            // in the AndroidView, a known category of Compose/WebView issue where forcing
            // a render layer interferes with the WebView's virtual accessibility node
            // tree — but removing that risks reintroducing whatever rendering glitch it
            // was added to work around, which needs a real device to see, not just a
            // build. Left in place pending that check.
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                settings.isAlgorithmicDarkeningAllowed = true
            }

            settings.apply {
                javaScriptEnabled = true
                cacheMode = WebSettings.LOAD_NO_CACHE
                domStorageEnabled = true
                blockNetworkImage = false
                loadsImagesAutomatically = true
                if (backupFormat != BackupFormat.HTML) {
                    isLongClickable = false
                    setOnLongClickListener { true }
                    isHapticFeedbackEnabled = false
                }
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
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
                        val intent = Intent(Intent.ACTION_VIEW, url).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }

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

                    // The WebView cannot infer the app theme - the app theme never
                    // declares isLightTheme - so tell the page directly. The report
                    // keeps its own prefers-color-scheme rules for when it is opened
                    // anywhere else.
                    val night = (configuration.uiMode and
                            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                    view?.evaluateJavascript(
                        "document.documentElement.dataset.theme = '${if (night) "dark" else "light"}';"
                    ) { }

                    if (backupFormat == BackupFormat.HTML) {
                        val script = "setInstalledApps([$packagesList])"
                        view?.evaluateJavascript(script) { }
                    }
                }

            }

            if (backupFormat != BackupFormat.HTML) {
                addJavascriptInterface(
                    TableBridge(context),
                    "Android"
                )
            }
        }
    }


    val activeScreen by mainActivityViewModel.activeScreen.collectAsState()
    LaunchedEffect(contentKey,activeScreen) {
        webView.stopLoading()
        //viewModel.clearHtml()
        //webView.loadUrl("about:blank")
        if (activeScreen != MainActivityViewModel.ActiveScreen.READER) {
            // Found on real hardware (issue E3): the most-recent backup could show as a
            // blank pane on first entering this tab, rendering only after picking a
            // different backup and coming back. Suspected cause: the AndroidView update
            // below skips reloading when the content hash tag already matches, so
            // stopLoading() interrupting a load while this tab was inactive could leave
            // the WebView blank while the tag still claims that content is showing.
            // Clearing the tag here means the next entry always reloads for real instead
            // of trusting a load stopLoading() may have interrupted. Not confirmed against
            // real TalkBack/UI behaviour — needs a real-device check, not just a build.
            webView.setTag(CONTENT_HASH_TAG, null)
            return@LaunchedEffect
        }
        if (tempContent != null) {
            when (backupFormat) {
                BackupFormat.HTML -> {
                    viewModel.loadHtml(context,null,tempContent)
                }

                BackupFormat.CSV -> {
                    viewModel.loadCsv(
                        context,
                        null,
                        tempContent,
                        textColor,
                        linkColor,
                        codeBg,
                        bgColor
                    )
                }

                BackupFormat.Markdown -> {
                    viewModel.loadMD(
                        context,
                        null,
                        tempContent,
                        textColor,
                        linkColor,
                        codeBg,
                        bgColor
                    )
                }
            }

            return@LaunchedEffect
        }
        if (uri != null) {
            when (backupFormat) {
                BackupFormat.CSV -> viewModel.loadCsv(
                    context,
                    uri,
                    null,
                    textColor,
                    linkColor,
                    codeBg,
                    bgColor
                )

                BackupFormat.Markdown -> viewModel.loadMD(
                    context,
                    uri,
                    null,
                    textColor,
                    linkColor,
                    codeBg,
                    bgColor
                )

                BackupFormat.HTML -> viewModel.loadHtml(context, uri, null)
            }
        }
    }

    Box(modifier = modifier) {
        val isReady = !convertState.isLoading && !convertState.html.isNullOrEmpty() //&& !isLoading
        if (isReady) {
                AndroidView(
                    factory = { webView },
                    modifier = modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 0.99f },
                    update = { view ->
                        val html = convertState.html ?: ""

                        val htmlHash = html.hashCode().toString()
                        val lastLoadedHash = view.getTag(CONTENT_HASH_TAG) as? String

                        if (htmlHash != lastLoadedHash) {
                            view.setTag(CONTENT_HASH_TAG, htmlHash)
                            if (html.isNotEmpty()) {
                                view.loadDataWithBaseURL(
                                    "file:///android_asset/",
                                    html,
                                    "text/html",
                                    "UTF-8",
                                    null
                                )
                            } else {
                                view.loadUrl("about:blank")
                            }
                        }
                    }
                )
        } else
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
    }


    DisposableEffect(webView) {
        onDispose {
            webView.apply {
                stopLoading()
                loadUrl("about:blank")
                clearHistory()
                removeAllViews()
                destroy()
            }
        }
    }
}