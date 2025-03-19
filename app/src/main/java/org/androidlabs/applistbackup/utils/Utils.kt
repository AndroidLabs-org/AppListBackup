package org.androidlabs.applistbackup.utils

import android.content.Context
import android.content.res.Configuration

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
}