package org.androidlabs.applistbackup.data

import androidx.annotation.StringRes
import org.androidlabs.applistbackup.R


enum class BackupApp(@StringRes val titleRes: Int) {
    USER(R.string.backup_app_user),
    SYSTEM(R.string.backup_app_system),
    DISABLED(R.string.backup_app_disabled);

    companion object {
        fun fromString(value: String): BackupApp =
            valueOf(value)
    }
}
