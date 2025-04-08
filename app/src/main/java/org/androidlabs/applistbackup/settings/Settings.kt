package org.androidlabs.applistbackup.settings

import android.app.Service.MODE_PRIVATE
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toUri
import org.androidlabs.applistbackup.data.BackupAppInfo
import org.androidlabs.applistbackup.data.BackupFormat

object Settings {
    private const val PREFERENCES_FILE: String = "preferences"
    private const val KEY_BACKUP_URI: String = "backup_uri"
    private const val KEY_BACKUP_FORMATS: String = "backup_formats"
    private const val KEY_BACKUP_EXCLUDE_DATA: String = "backup_exclude_data"
    private const val KEY_BACKUP_LIMIT: String = "backup_limit"

    fun getBackupUri(context: Context): Uri? {
        val sharedPreferences = context.getSharedPreferences(PREFERENCES_FILE, MODE_PRIVATE)
        return sharedPreferences.getString(KEY_BACKUP_URI, null)?.toUri()
    }

    fun setBackupUri(context: Context, uri: Uri) {
        val sharedPreferences = context.getSharedPreferences(PREFERENCES_FILE, MODE_PRIVATE)
        sharedPreferences.edit {
            putString(KEY_BACKUP_URI, uri.toString())
        }
    }

    fun getBackupFormats(context: Context): Set<BackupFormat> {
        val sharedPreferences = context.getSharedPreferences(PREFERENCES_FILE, MODE_PRIVATE)
        val formatString = sharedPreferences.getString(KEY_BACKUP_FORMATS, null)
        return formatString?.split(",")?.map { BackupFormat.fromString(it) }?.toSet() ?: setOf(
            BackupFormat.HTML
        )
    }

    fun setBackupFormats(context: Context, formats: Set<BackupFormat>) {
        val sharedPreferences = context.getSharedPreferences(PREFERENCES_FILE, MODE_PRIVATE)
        sharedPreferences.edit {
            putString(KEY_BACKUP_FORMATS, formats.joinToString(",") { it.value })
        }
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
        sharedPreferences.edit {
            putString(KEY_BACKUP_EXCLUDE_DATA, list.joinToString(",") { it.value })
        }
    }

    fun getBackupLimit(context: Context): Int {
        val sharedPreferences = context.getSharedPreferences(PREFERENCES_FILE, MODE_PRIVATE)
        return sharedPreferences.getInt(KEY_BACKUP_LIMIT, -1)
    }

    fun setBackupLimit(context: Context, value: Int) {
        val sharedPreferences = context.getSharedPreferences(PREFERENCES_FILE, MODE_PRIVATE)
        sharedPreferences.edit {
            putInt(KEY_BACKUP_LIMIT, value)
        }
    }

    fun observeBackupUri(
        context: Context,
        onChangeBackupUri: () -> Unit,
    ): SharedPreferences.OnSharedPreferenceChangeListener {
        return observeKeys(context, listOf(KEY_BACKUP_URI), {
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