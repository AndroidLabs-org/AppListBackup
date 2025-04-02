package org.androidlabs.applistbackup.data

import org.androidlabs.applistbackup.data.BackupFormat.entries


enum class BackupFormat(
    val value: String,
    private val extension: String,
    private val mimeType: String
) {
    HTML("HTML", "html", "text/html"),
    CSV("CSV", "csv", "text/csv"),
    Markdown("Markdown", "md", "text/markdown");

    fun fileExtension(): String = extension

    fun mimeType(): String = mimeType

    companion object {
        fun fromString(value: String): BackupFormat =
            fromStringOptional(value) ?: throw IllegalArgumentException("Unknown format: $value")

        fun fromStringOptional(value: String): BackupFormat? =
            entries.find { it.value == value }

        fun fromExtension(extension: String): BackupFormat =
            entries.find { it.extension == extension.lowercase() }
                ?: throw IllegalArgumentException("Unknown extension: $extension")
    }
}
