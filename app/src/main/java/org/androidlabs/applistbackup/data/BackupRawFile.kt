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
                // Inside the chosen folder, not beside it. This used to be file.parentFile,
                // which wrote every TV backup one directory above the folder the user picked,
                // where the app's own listing could never find it — so the viewer stayed
                // empty and the retention limit never deleted anything. deleteFiles already
                // resolved names against `file`, so create and delete disagreed.
                val newFile = File(file, fileName)
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

    fun deleteFile(fileName: String) = deleteFiles(setOf(fileName))

    /**
     * Deletes every named file using a single directory listing.
     *
     * `DocumentFile.listFiles()` is a Storage Access Framework round trip whose cost grows
     * with the folder, and deleting one name at a time paid it once per name. A backup that
     * writes three formats was doing six listings, which measured 10.5 s against 0.5 s for a
     * single format — twenty times the cost, for work that should have been about three.
     */
    fun deleteFiles(fileNames: Set<String>) {
        if (fileNames.isEmpty()) return
        when {
            documentFile != null -> {
                documentFile.listFiles()
                    .filter { it.name in fileNames }
                    .forEach { it.delete() }
            }

            file != null -> {
                fileNames.forEach { File(file, it).delete() }
            }
        }
    }

    companion object {
        fun fromFile(file: File, context: Context) = BackupRawFile(file, null, context)

        fun fromDocumentFile(documentFile: DocumentFile, context: Context) =
            BackupRawFile(null, documentFile, context)
    }
}