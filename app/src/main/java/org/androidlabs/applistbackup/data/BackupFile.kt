package org.androidlabs.applistbackup.data

import android.net.Uri
import java.util.Date

data class BackupFile(
    val uri: Uri,
    val date: Date,
    val title: String,
    val generationNumber: Int,
    val generationsCount: Int,
) {
    fun titleWithGeneration(): String {
        return "$title (${generationNumber + 1} of $generationsCount)"
    }
}