package org.androidlabs.applistbackup.data


enum class BackupFormat(
    val value: String,
    val extension: String,
    private val mimeType: String
) {
    HTML("HTML", "html", "text/html"),
    CSV("CSV", "csv", "text/csv"),
    Markdown("Markdown", "md", "text/markdown");

    fun fileExtension(): String = extension

    fun mimeType(): String = mimeType

    companion object {
        // Accepts the display name ("Markdown") or the file extension ("md") —
        // the output files are named by extension, so that's the natural thing
        // for an automation author to type, and it silently fell back to the
        // saved setting instead of being recognised.
        fun fromString(value: String): BackupFormat? =
            entries.find {
                it.value.equals(value, ignoreCase = true) ||
                        it.extension.equals(value, ignoreCase = true)
            }

        fun fromExtension(extension: String): BackupFormat? =
            entries.find { it.extension.equals(extension, ignoreCase = true) }
    }
}
