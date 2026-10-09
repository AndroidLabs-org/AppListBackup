package org.androidlabs.applistbackup.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.webkit.JavascriptInterface
import android.widget.Toast
import org.androidlabs.applistbackup.R

class TableBridge(private val context: Context) {
    @JavascriptInterface
    fun copyRow(text: String) {
        val clipboard =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        clipboard.setPrimaryClip(
            ClipData.newPlainText("row", text)
        )

        Toast.makeText(
            context.applicationContext,
            context.resources.getString(R.string.text_copied),
            Toast.LENGTH_SHORT
        ).show()
    }
}