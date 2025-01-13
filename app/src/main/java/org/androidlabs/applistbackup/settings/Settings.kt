package org.androidlabs.applistbackup.settings

import android.app.Service.MODE_PRIVATE
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import org.androidlabs.applistbackup.data.BackupAppInfo
import org.androidlabs.applistbackup.data.BackupFormat

object Settings {
    private const val PREFERENCES_FILE: String = "preferences"
    private const val KEY_BACKUP_URI: String = "backup_uri"
    private const val KEY_BACKUP_FORMAT: String = "backup_format"
    private const val KEY_BACKUP_EXCLUDE_DATA: String = "backup_exclude_data"

    fun getBackupUri(context: Context): Uri? {
        val sharedPreferences = context.getSharedPreferences(PREFERENCES_FILE, MODE_PRIVATE)
        val uriString = sharedPreferences.getString(KEY_BACKUP_URI, null)
        return if (uriString != null) Uri.parse(uriString) else null
    }

    fun setBackupUri(context: Context, uri: Uri) {
        val sharedPreferences = context.getSharedPreferences(PREFERENCES_FILE, MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString(KEY_BACKUP_URI, uri.toString())
        editor.apply()
    }

    fun getBackupFormat(context: Context): BackupFormat {
        val sharedPreferences = context.getSharedPreferences(PREFERENCES_FILE, MODE_PRIVATE)
        val formatString = sharedPreferences.getString(KEY_BACKUP_FORMAT, null)
        return if (formatString != null) BackupFormat.fromString(formatString) else BackupFormat.HTML
    }

    fun setBackupFormat(context: Context, format: BackupFormat) {
        val sharedPreferences = context.getSharedPreferences(PREFERENCES_FILE, MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString(KEY_BACKUP_FORMAT, format.value)
        editor.apply()
    }

    fun getBackupExcludeData(context: Context): List<BackupAppInfo> {
        val sharedPreferences = context.getSharedPreferences(PREFERENCES_FILE, MODE_PRIVATE)
        val string = sharedPreferences.getString(KEY_BACKUP_EXCLUDE_DATA, null)
        if (string.isNullOrEmpty()) {
            return emptyList()
        }
        return string.split(",").map { BackupAppInfo.fromString(it) }
    }

    fun setBackupExcludeData(context: Context, list: List<BackupAppInfo>) {
        val sharedPreferences = context.getSharedPreferences(PREFERENCES_FILE, MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString(KEY_BACKUP_EXCLUDE_DATA, list.map { it.value }.joinToString(","))
        editor.apply()
    }

    fun observeBackupUri(
        context: Context,
        onChangeBackupUri: () -> Unit,
    ): SharedPreferences.OnSharedPreferenceChangeListener {
        return observeKeys(context, listOf(KEY_BACKUP_URI), { key ->
            onChangeBackupUri()
        })
    }

    private fun observeKeys(
        context: Context,
        keys: List<String>,
        onChange: (key: String) -> Unit
    ): SharedPreferences.OnSharedPreferenceChangeListener {
        val sharedPreferences = context.getSharedPreferences(PREFERENCES_FILE, MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key != null && keys.contains(key)) {
                onChange(key)
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