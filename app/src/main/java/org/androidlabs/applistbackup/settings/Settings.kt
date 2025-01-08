package org.androidlabs.applistbackup.settings

import android.app.Service.MODE_PRIVATE
import android.content.Context
import android.net.Uri

object Settings {
    private const val PREFERENCES_FILE: String = "preferences"
    private const val KEY_BACKUP_URI: String = "backup_uri"

    fun setBackupUri(context: Context, uri: Uri) {
        val sharedPreferences = context.getSharedPreferences(PREFERENCES_FILE, MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString(KEY_BACKUP_URI, uri.toString())
        editor.apply()
    }

    fun getBackupUri(context: Context): Uri? {
        val sharedPreferences = context.getSharedPreferences(PREFERENCES_FILE, MODE_PRIVATE)
        val uriString = sharedPreferences.getString(KEY_BACKUP_URI, null)
        return if (uriString != null) Uri.parse(uriString) else null
    }
}