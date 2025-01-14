package org.androidlabs.applistbackup.reader

import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun BackupCsvView(
    modifier: Modifier = Modifier,
    uri: Uri?
) {
    val context = LocalContext.current

    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            webViewClient = WebViewClient()
        }
    }

    LaunchedEffect(uri) {
        uri?.let {
            val csvContent = context.contentResolver.openInputStream(it)?.use { stream ->
                stream.bufferedReader().readText()
            } ?: return@let

            val htmlContent = csvToHtml(csvContent)
            webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
        }
    }

    AndroidView(
        factory = { webView },
        modifier = modifier
    )
}

private fun csvToHtml(csv: String): String {
    val rows = csv.lines().filter { it.isNotBlank() }
    val htmlBuilder = StringBuilder()
    htmlBuilder.append(
        """
        <html>
        <head>
        <style>
            table {
                border-collapse: collapse;
                width: 100%;
            }
            th, td {
                padding: 8px;
                border: 1px solid #ddd;
                text-align: left;
            }
            th {
                background-color: #f1f1f1;
                font-weight: bold;
                position: sticky;
                top: 0;
                z-index: 1;
            }
        </style>
        </head>
        <body>
        <table>
        <thead>
        """
    )

    val headerColumns = rows.first().split(",")
    htmlBuilder.append("<tr>")
    for (header in headerColumns) {
        val cleanedHeader = header.trim()
            .removeSurrounding("\"")
            .replace(" ", "&nbsp;")
        htmlBuilder.append("<th>$cleanedHeader</th>")
    }
    htmlBuilder.append("</tr></thead><tbody>")

    for (row in rows.drop(1)) {
        htmlBuilder.append("<tr>")
        val columns = row.split(",")
        for (column in columns) {
            val cleanedColumn = column.trim()
                .removeSurrounding("\"")
                .replace(" ", "&nbsp;")
            htmlBuilder.append("<td>$cleanedColumn</td>")
        }
        htmlBuilder.append("</tr>")
    }
    htmlBuilder.append("</tbody></table></body></html>")
    return htmlBuilder.toString()
}