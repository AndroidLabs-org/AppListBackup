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

    fun delete(): Boolean {
        return when {
            documentFile != null -> {
                try {
                    documentFile.delete()
                } catch (e: Exception) {
                    Log.e("BackupService", "DocumentFile delete failed: $e")
                    false
                }
            }

            file != null -> {
                try {
                    file.delete()
                } catch (e: Exception) {
                    Log.e("BackupService", "File delete failed: $e")
                    false
                }
            }

            else -> throw IllegalStateException("Neither file nor documentFile is available")
        }
    }

    fun renameTo(newName: String): BackupRawFile? {
        return when {
            documentFile != null -> {
                try {
                    val parent = documentFile.parentFile
                    val existingFile = parent?.listFiles()?.find { it.name == newName }

                    if (existingFile != null) {
                        if (!existingFile.delete()) {
                            Log.e(
                                "BackupService",
                                "Could not delete existing DocumentFile: ${existingFile.uri}"
                            )
                            return null
                        }
                    }

                    if (documentFile.renameTo(newName)) {
                        fromDocumentFile(documentFile, context)
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    Log.e("BackupService", "DocumentFile rename failed: $e")
                    null
                }
            }

            file != null -> {
                try {
                    val destination = File(file.parentFile, newName)
                    if (destination.exists()) {
                        if (!destination.delete()) {
                            Log.e(
                                "BackupService",
                                "Could not delete existing file: ${destination.absolutePath}"
                            )
                            return null
                        }
                    }

                    if (file.renameTo(destination)) {
                        fromFile(destination, context)
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    Log.e("BackupService", "File rename failed: $e")
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