package org.androidlabs.applistbackup.data

import android.net.Uri
import java.util.Date

data class FileInfo(
    val uri: Uri,
    val name: String,
    val format: String,
    val date: Date
)
