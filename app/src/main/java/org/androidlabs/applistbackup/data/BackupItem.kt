package org.androidlabs.applistbackup.data

sealed class BackupItem {
    data class File(val file: BackupFile) : BackupItem()
    data class Temp(val format: BackupFormat, val content: String) : BackupItem()
}