package org.androidlabs.applistbackup.settings

import android.app.Service.MODE_PRIVATE
import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import org.androidlabs.applistbackup.data.BackupApp
import org.androidlabs.applistbackup.data.BackupAppInfo
import org.androidlabs.applistbackup.data.BackupFormat
import org.androidlabs.applistbackup.data.SortBy
import org.androidlabs.applistbackup.data.SortOrder

object Settings {
    private const val PREFERENCES_FILE: String = "preferences"
    private const val KEY_BACKUP_URI: String = "backup_uri"
    private const val KEY_BACKUP_FORMATS: String = "backup_formats"

    private const val KEY_SORT_BY: String = "backup_sort_by"

    private const val KEY_SORT_ORDER: String = "backup_sort_order"

    private const val KEY_INCLUDE_SYSTEM_INFO: String = "backup_include_system_info"
    private const val KEY_BACKUP_APPS: String = "backup_apps"

    private const val KEY_BACKUP_EXCLUDE_DATA: String = "backup_exclude_data"

    private const val KEY_CREATE_LATEST_BACKUP: String = "create_latest_backup"
    private const val KEY_BACKUP_EXCLUDED_PACKAGES: String = "backup_exclude_packages"
    private const val KEY_BACKUP_LIMIT: String = "backup_limit"

    fun getBackupUri(context: Context): Uri? {
        val sharedPreferences = context.getSharedPreferences(PREFERENCES_FILE, MODE_PRIVATE)
        val uriString = sharedPreferences.getString(KEY_BACKUP_URI, null) ?: return null
        val uri = uriString.toUri()

        // Android TV has no Storage Access Framework picker: TvFolderPickerActivity returns
        // Uri.fromFile(folder), and BackupService branches on isTV to treat the value as a
        // plain File. Validating that through DocumentFile.fromTreeUri — which expects a
        // content:// tree URI — always failed, so the folder a TV user chose was written and
        // then discarded on the very next read, leaving the app unable to back up at all.
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            val directory = uri.toFile()
            if (!directory.isDirectory || !directory.canWrite()) {
                // A folder that is gone is a definitive answer and worth forgetting. One that
                // is still there but momentarily refuses a write is not — an unmounted volume
                // or a busy filesystem says nothing about the user's choice.
                if (!directory.exists()) {
                    sharedPreferences.edit { remove(KEY_BACKUP_URI) }
                }
                return null
            }
            return uri
        }

        val doc = DocumentFile.fromTreeUri(context, uri)

        if (doc == null || !doc.exists() || !doc.canWrite()) {
            // Forget the folder only when the grant itself is gone — the user revoked it, or
            // the volume holding it was removed. Everything else is treated as a bad moment
            // rather than a decision.
            //
            // This used to delete the setting on any failed check. exists() and canWrite() are
            // provider round trips and can fail transiently, and losing the setting is not a
            // small thing: every trigger — widget, Tasker, shortcut, notification — then does
            // nothing, silently, until the user notices and picks the folder again. A test run
            // on API 31 showed exactly that shape, one engine case finding no folder mid-class
            // when nothing else could have removed it.
            val grantHeld = context.contentResolver.persistedUriPermissions.any {
                it.uri == uri && it.isWritePermission
            }
            if (!grantHeld) {
                sharedPreferences.edit { remove(KEY_BACKUP_URI) }
            }
            return null
        }

        return uri
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
        return formatString
            ?.split(",")
            ?.mapNotNull { BackupFormat.fromString(it.trim()) }
            ?.toSet()
            ?: setOf(BackupFormat.HTML)
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
        // Same hardening as getBackupApps: BackupAppInfo.fromString throws on anything it
        // does not recognise, so a value written by a newer version would crash an older one.
        return string.split(",")
            .mapNotNull { entry ->
                val name = entry.trim()
                if (name.isEmpty()) null
                else runCatching { BackupAppInfo.fromString(name) }.getOrNull()
            }
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

    fun getBackupApps(context: Context): Set<BackupApp> {
        val sharedPreferences = context.getSharedPreferences(PREFERENCES_FILE, MODE_PRIVATE)
        val stored = sharedPreferences.getString(KEY_BACKUP_APPS, null)
            ?: return setOf(BackupApp.USER, BackupApp.SYSTEM, BackupApp.DISABLED)

        // Deselecting every category stores "", and "".split(",") yields [""], which
        // BackupApp.fromString cannot parse — it threw, poisoning this getter for every
        // later caller including the Settings screen that wrote the value. Blank and
        // unrecognised entries are skipped instead, so "nothing selected" reads back as an
        // empty set and a value written by a newer version cannot break an older one.
        return stored.split(",")
            .mapNotNull { entry ->
                val name = entry.trim()
                if (name.isEmpty()) null
                else runCatching { BackupApp.fromString(name) }.getOrNull()
            }
            .toSet()
    }

    fun setBackupApps(context: Context, apps: Set<BackupApp>) {
        val sharedPreferences = context.getSharedPreferences(PREFERENCES_FILE, MODE_PRIVATE)
        sharedPreferences.edit {
            putString(KEY_BACKUP_APPS, apps.joinToString(",") { it.name })
        }
    }

    fun getBackupSortBy(context: Context): SortBy {
        val ordinal = context
            .getSharedPreferences(PREFERENCES_FILE, MODE_PRIVATE)
            .getInt(KEY_SORT_BY, SortBy.INSTALL_TIME.ordinal)
        return SortBy.entries.getOrElse(ordinal) {
            SortBy.INSTALL_TIME
        }
    }

    fun setBackupSortBy(context: Context, sort: SortBy) {
        val sharedPreferences = context.getSharedPreferences(PREFERENCES_FILE, MODE_PRIVATE)
        sharedPreferences.edit {
            putInt(KEY_SORT_BY, sort.ordinal)
        }
    }

    fun getBackupSortOrder(context: Context): SortOrder {
        val ordinal = context
            .getSharedPreferences(PREFERENCES_FILE, MODE_PRIVATE)
            .getInt(KEY_SORT_ORDER, SortOrder.DESCENDING.ordinal)
        return SortOrder.entries.getOrElse(ordinal) {
            SortOrder.DESCENDING
        }
    }

    fun setBackupSortOrder(context: Context, order: SortOrder) {
        val sharedPreferences = context.getSharedPreferences(PREFERENCES_FILE, MODE_PRIVATE)
        sharedPreferences.edit {
            putInt(KEY_SORT_ORDER, order.ordinal)
        }
    }

    fun getBackupExcludedPackages(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFERENCES_FILE, MODE_PRIVATE)
        return prefs.getStringSet(
            KEY_BACKUP_EXCLUDED_PACKAGES,
            emptySet()
        ) ?: emptySet()
    }

    fun setBackupExcludedPackages(
        context: Context,
        packages: Set<String>
    ) {
        val prefs = context.getSharedPreferences(PREFERENCES_FILE, MODE_PRIVATE)

        prefs.edit {
            putStringSet(
                KEY_BACKUP_EXCLUDED_PACKAGES,
                packages
            )
        }
    }

    fun getIncludeSystemInfo(context: Context): Boolean {
        return context
            .getSharedPreferences(PREFERENCES_FILE, MODE_PRIVATE)
            .getBoolean(KEY_INCLUDE_SYSTEM_INFO, false)
    }

    fun setIncludeSystemInfo(context: Context, value: Boolean) {
        val sharedPreferences = context.getSharedPreferences(PREFERENCES_FILE, MODE_PRIVATE)
        sharedPreferences.edit {
            putBoolean(KEY_INCLUDE_SYSTEM_INFO, value)
        }
    }

    fun getCreateLatestBackup(context: Context): Boolean {
        return context
            .getSharedPreferences(PREFERENCES_FILE, MODE_PRIVATE)
            .getBoolean(KEY_CREATE_LATEST_BACKUP, false)
    }

    fun setCreateLatestBackup(context: Context, value: Boolean) {
        val sharedPreferences = context.getSharedPreferences(PREFERENCES_FILE, MODE_PRIVATE)
        sharedPreferences.edit {
            putBoolean(KEY_CREATE_LATEST_BACKUP, value)
        }
    }

}