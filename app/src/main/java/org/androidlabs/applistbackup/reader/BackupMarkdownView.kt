package org.androidlabs.applistbackup.reader

import android.net.Uri
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.core.MarkwonTheme

@Composable
fun BackupMarkdownView(
    modifier: Modifier = Modifier,
    uri: Uri?
) {
    val context = LocalContext.current

    val textColor = MaterialTheme.colorScheme.onSurface
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBackgroundColor = MaterialTheme.colorScheme.surface

    val textView = remember(textColor) {
        TextView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setTextIsSelectable(true)
            setTextColor(textColor.toArgb())
        }
    }

    val markwon = remember(textColor, linkColor, codeBackgroundColor) {
        Markwon.builder(context)
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    builder
                        .linkColor(linkColor.toArgb())
                        .codeTextColor(textColor.toArgb())
                        .codeBlockTextColor(textColor.toArgb())
                        .codeBackgroundColor(codeBackgroundColor.toArgb())
                        .listItemColor(textColor.toArgb())
                        .blockQuoteColor(textColor.toArgb())
                }
            })
            .build()
    }

    LaunchedEffect(uri, markwon) {
        uri?.let {
            val markdownContent = context.contentResolver.openInputStream(it)?.use { stream ->
                stream.bufferedReader().readText()
            } ?: return@let

            markwon.setMarkdown(textView, markdownContent)
        }
    }

    AndroidView(
        factory = { textView },
        modifier = modifier
    )
}
