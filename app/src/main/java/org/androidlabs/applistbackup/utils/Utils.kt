package org.androidlabs.applistbackup.utils

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.graphics.Color

object Utils {
    fun clearPrefixSlash(path: String): String {
        val prefix = "/"
        if (path.startsWith(prefix)) {
            return path.removePrefix(prefix)
        }
        return path
    }

    fun isTV(context: Context): Boolean {
        val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
        return uiMode == Configuration.UI_MODE_TYPE_TELEVISION
    }

    fun Color.toCssHex(): String {
        val red = (red * 255).toInt()
        val green = (green * 255).toInt()
        val blue = (blue * 255).toInt()

        return String.format("#%02X%02X%02X", red, green, blue)
    }

    fun hexToRgba(hex: String, alpha: Float): String {
        val clean = hex.removePrefix("#")

        val r = clean.substring(0, 2).toInt(16)
        val g = clean.substring(2, 4).toInt(16)
        val b = clean.substring(4, 6).toInt(16)

        return "rgba($r,$g,$b,$alpha)"
    }

    fun renderCell(text: String, linkColor: String): String {

        val linkRegex = Regex("""\[(.+?)]\((https?://[^)]+)\)""")

        return text.replace(linkRegex) { m ->
            val label = m.groupValues[1]
            val url = m.groupValues[2]

            """<a href="$url" style="color:$linkColor;text-decoration:none;">$label</a>"""
        }
    }

    /**
     * Splits an ATX Markdown heading ("## Installed apps") into its level (1-6) and text,
     * or returns null if the line is not a heading. The viewer's Markdown renderer handles
     * only tables and paragraphs, so without this the "##" reached the screen as literal text.
     */
    fun markdownHeading(line: String): Pair<Int, String>? {
        val m = Regex("""^\s{0,3}(#{1,6})\s+(.+?)\s*#*\s*$""").find(line) ?: return null
        return m.groupValues[1].length to m.groupValues[2]
    }
}
