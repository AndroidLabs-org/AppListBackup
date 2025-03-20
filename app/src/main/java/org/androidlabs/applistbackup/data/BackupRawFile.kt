package org.androidlabs.applistbackup.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import java.io.File

class BackupRawFile private constructor(
    private val file: File?,
    private val documentFile: DocumentFile?,
    private val context: Context
) {
    init {
        require((file != null) xor (documentFile != null)) {
            "Either file or documentFile must be provided, but not both"
        }
    }

    val uri: Uri
        get() = when {
            documentFile != null -> documentFile.uri
            file != null -> file.toUri()
            else -> throw IllegalStateException("Neither file nor documentFile is available")
        }

    val lastModified: Long
        get() = when {
            documentFile != null -> documentFile.lastModified()
            file != null -> file.lastModified()
            else -> throw IllegalStateException("Neither file nor documentFile is available")
        }

    val name: String
        get() = when {
            documentFile != null -> documentFile.name ?: ""
            file != null -> file.name
            else -> throw IllegalStateException("Neither file nor documentFile is available")
        }

    fun createFile(mimeType: String, fileName: String): BackupRawFile? {
        return when {
            documentFile != null -> {
                documentFile.createFile(mimeType, fileName)?.let { newDoc ->
                    fromDocumentFile(newDoc, context)
                }
            }
            file != null -> {
                val newFile = File(file.parentFile, fileName)
                try {
                    if (newFile.createNewFile()) {
                        fromFile(newFile, context)
                    } else null
                } catch (exception: Exception) {
                    Log.e("BackupService", "Create file failed: $exception")
                    null
                }
            }
            else -> throw IllegalStateException("Neither file nor documentFile is available")
        }
    }

    companion object {
        fun fromFile(file: File, context: Context) = BackupRawFile(file, null, context)

        fun fromDocumentFile(documentFile: DocumentFile, context: Context) =
            BackupRawFile(null, documentFile, context)
    }
}