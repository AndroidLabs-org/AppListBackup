package org.androidlabs.applistbackup.data

data class BackupFormatResult(
    val format: BackupFormat,
    val file: BackupRawFile?,
    val exception: Exception?
) {
    fun isSuccess(): Boolean {
        return file != null
    }
}
