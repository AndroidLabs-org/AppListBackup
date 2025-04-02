package org.androidlabs.applistbackup.data

import android.content.Context
import org.androidlabs.applistbackup.R
import org.androidlabs.applistbackup.data.BackupAppInfo.entries

enum class BackupAppInfo(val value: String) {
    Package("Package"),
    System("System"),
    Enabled("Enabled"),
    Version("Version"),
    TargetSDK("TargetSDK"),
    MinSDK("MinSDK"),
    InstalledAt("InstalledAt"),
    UpdatedAt("UpdatedAt"),
    InstallSource("InstallSource"),
    Links("Links");

    companion object {
        fun fromString(value: String): BackupAppInfo {
            return entries.find { it.value == value } as BackupAppInfo
        }
    }

    fun title(context: Context): String {
        val titleId = when (this) {
            Package -> R.string.package_title
            System -> R.string.system_title
            Enabled -> R.string.enabled_title
            Version -> R.string.version_title
            TargetSDK -> R.string.target_sdk_version_title
            MinSDK -> R.string.min_sdk_version_title
            InstalledAt -> R.string.installed_at_title
            UpdatedAt -> R.string.updated_at_title
            InstallSource -> R.string.installer
            Links -> R.string.links_title
        }
        return context.getString(titleId)
    }
}