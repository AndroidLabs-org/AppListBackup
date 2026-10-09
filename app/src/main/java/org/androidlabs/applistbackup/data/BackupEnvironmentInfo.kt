package org.androidlabs.applistbackup.data

import kotlinx.serialization.Serializable

@Serializable
data class BackupEnvironmentInfo(
    val androidVersion: String,
    val apiLevel: Int,
    val manufacturer: String,
    val model: String,
    val device: String,
    val product: String,
    val buildId: String,
    val fingerprint: String,
    val bootloader: String,
    val hardware: String,
    val securityPatch: String?,
    val supportedAbis: List<String>,
    val locale: String,
    val timezone: String
)

fun BackupEnvironmentInfo.toHtml(): String {
    return """
        <b>System Environment</b>
        ${envStat("Android", "$androidVersion (API $apiLevel)")}
        ${envStat("Manufacturer", manufacturer)}
        ${envStat("Model", model)}
        ${envStat("Device", device)}
        ${envStat("Product", product)}
        ${envStat("Build ID", buildId)}
        ${envStat("Security Patch", securityPatch ?: "Unknown")}
        ${envStat("Bootloader", bootloader)}
        ${envStat("Hardware", hardware)}
        ${envStat("Locale", locale)}
        ${envStat("Timezone", timezone)}
    """.trimIndent()
}

fun BackupEnvironmentInfo.toMarkdown(): String {
    val info = buildString {
            appendLine("## System Environment")
            appendLine()
            appendLine("- Android: ${androidVersion} (API ${apiLevel})")
            appendLine("- Manufacturer: ${manufacturer}")
            appendLine("- Model: ${model}")
            appendLine("- Device: ${device}")
            appendLine("- Product: ${product}")
            appendLine("- Build ID: ${buildId}")
            appendLine("- Security Patch: ${securityPatch ?: "Unknown"}")
            appendLine("- Bootloader: ${bootloader}")
            appendLine("- Hardware: ${hardware}")
            appendLine("- Locale: ${locale}")
            appendLine("- Timezone: ${timezone}")
        }
    return info
}

private fun envStat(title: String, value: String) =
    """
    <div class="stat-item">
        <b style="min-width:140px;">$title</b>
        <p style="text-align:left;overflow-wrap:anywhere;word-break:break-word;">$value</p>
    </div>
    """