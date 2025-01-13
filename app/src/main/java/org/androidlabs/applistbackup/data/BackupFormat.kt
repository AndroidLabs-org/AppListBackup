package org.androidlabs.applistbackup.data

enum class BackupFormat(val value: String) {
    HTML("HTML"),
    Markdown("Markdown");

    companion object {
        fun fromString(value: String): BackupFormat {
            return entries.find { it.value == value } as BackupFormat
        }

        fun fromStringOptional(value: String): BackupFormat? {
            return entries.find { it.value == value }
        }

        fun fromExtension(extension: String): BackupFormat {
            return when (extension.lowercase()) {
                "html" -> HTML
                "md" -> Markdown
                else -> throw IllegalArgumentException("Unknown extension: $extension")
            }
        }
    }

    fun fileExtension(): String {
        return when (this) {
            HTML -> "html"
            Markdown -> "md"
        }
    }
}
