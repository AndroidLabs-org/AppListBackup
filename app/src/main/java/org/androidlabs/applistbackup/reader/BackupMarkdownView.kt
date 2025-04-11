package org.androidlabs.applistbackup.reader

import android.annotation.SuppressLint
import android.net.Uri
import android.text.Spanned
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.recycler.MarkwonAdapter
import org.androidlabs.applistbackup.R
import org.commonmark.ext.gfm.tables.TableBlock
import io.noties.markwon.recycler.table.TableEntry
import io.noties.markwon.recycler.table.TableEntryPlugin
import io.noties.markwon.utils.Dip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@SuppressLint("NotifyDataSetChanged")
@Composable
fun BackupMarkdownView(
    modifier: Modifier = Modifier,
    uri: Uri?
) {
    val context = LocalContext.current

    val textColor = MaterialTheme.colorScheme.onSurface
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBackgroundColor = MaterialTheme.colorScheme.surface

    val recyclerView = remember(textColor) {
        RecyclerView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            layoutManager = LinearLayoutManager(context)

            val adapter = MarkwonAdapter.builder(R.layout.adapter_node, R.id.textView)
                .include<TableBlock>(TableBlock::class.java, TableEntry.create { builder ->
                    builder.tableLayout(R.layout.adapter_table_block, R.id.table_layout)
                    builder.textLayoutIsRoot(R.layout.view_table_entry_cell)
                })
                .build()

            this.adapter = adapter
        }
    }

    val markwon = remember(textColor, linkColor, codeBackgroundColor) {
        Markwon.builder(context)
            .usePlugin(TableEntryPlugin.create { builder ->
                val dip = Dip.create(context)
                builder.tableCellPadding(dip.toPx(12))
                builder.tableBorderWidth(dip.toPx(1))
                builder.tableBorderColor(textColor.copy(alpha = 75f / 255f).toArgb())
                builder.tableEvenRowBackgroundColor(textColor.copy(alpha = 22f / 255f).toArgb())
            })
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

                override fun beforeSetText(textView: TextView, markdown: Spanned) {
                    textView.setTextColor(textColor.toArgb())
                    super.beforeSetText(textView, markdown)
                }
            })
            .build()
    }

    LaunchedEffect(uri, markwon) {
        uri?.let {
            val markdownContent = context.contentResolver.openInputStream(it)?.use { stream ->
                stream.bufferedReader().readText()
            } ?: return@let

            withContext(Dispatchers.Main) {
                val adapter = recyclerView.adapter as MarkwonAdapter
                adapter.setMarkdown(markwon, markdownContent)
                adapter.notifyDataSetChanged()
            }
        }
    }

    AndroidView(
        factory = { recyclerView },
        modifier = modifier.clipToBounds()
    )
}
