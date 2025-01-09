package org.androidlabs.applistbackup.settings

import android.app.Service.MODE_PRIVATE
import android.content.Context
import android.content.SharedPreferences
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

    fun observeBackupUri(context: Context, onChange: (Uri?) -> Unit): SharedPreferences.OnSharedPreferenceChangeListener {
        val sharedPreferences = context.getSharedPreferences(PREFERENCES_FILE, MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_BACKUP_URI) {
                val newUri = getBackupUri(context)
                onChange(newUri)
            }
        }

        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)

        return listener
    }

    fun unregisterListener(
        context: Context, listener:
    SharedPreferences.OnSharedPreferenceChangeListener
    ) {
        context.getSharedPreferences(PREFERENCES_FILE, MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(listener)
    }
}