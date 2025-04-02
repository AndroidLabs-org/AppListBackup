package org.androidlabs.applistbackup

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.DocumentsContract
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.graphics.createBitmap
import androidx.core.net.toFile
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.androidlabs.applistbackup.data.BackupAppDetails
import org.androidlabs.applistbackup.data.BackupAppInfo
import org.androidlabs.applistbackup.data.BackupFormat
import org.androidlabs.applistbackup.data.BackupRawFile
import org.androidlabs.applistbackup.settings.Settings
import org.androidlabs.applistbackup.utils.Utils.clearPrefixSlash
import org.androidlabs.applistbackup.utils.Utils.isTV
import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

data class BackupFile(
    val uri: Uri,
    val date: Date,
    val title: String
)

class BackupService : Service() {
    private val tag: String = "BackupService"

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    companion object {
        const val SERVICE_CHANNEL_ID = "BackupService"
        const val BACKUP_CHANNEL_ID = "Backup"

        const val FILE_NAME_PREFIX = "app-list-backup-"

        val isRunning = MutableStateFlow(false)

        private var onCompleteCallback: ((Uri) -> Unit)? = null

        fun getBackupFolder(context: Context): BackupRawFile? {
            val backupsUri = Settings.getBackupUri(context) ?: return null
            if (isTV(context)) {
                return BackupRawFile.fromFile(backupsUri.toFile(), context)
            } else {
                DocumentFile.fromTreeUri(context, backupsUri)?.let {
                    return BackupRawFile.fromDocumentFile(it, context)
                }

                return null
            }
        }

        fun getReadablePathFromUri(context: Context, uri: Uri?): String {
            var decodedPath = ""
            if (uri == null) {
                return decodedPath
            }
            var type = "raw"

            if (isTV(context)) {
                decodedPath = uri.path!!
            } else {
                val docId = DocumentsContract.getTreeDocumentId(uri)
                val split = docId.split(":")
                type = split[0]
                val path = split.getOrNull(1) ?: ""
                decodedPath = URLDecoder.decode(path, StandardCharsets.UTF_8.toString())
            }

            return when (type) {
                "primary" -> "Internal Storage/${clearPrefixSlash(decodedPath)}"
                "home" -> "Home/${clearPrefixSlash(decodedPath)}"
                "raw" -> {
                    val internalPath = Environment.getExternalStorageDirectory().path
                    when {
                        decodedPath.startsWith(internalPath) -> {
                            "Internal Storage/${
                                clearPrefixSlash(
                                    decodedPath.removePrefix(
                                        internalPath
                                    )
                                )
                            }"
                        }

                        else -> {
                            decodedPath
                        }
                    }
                }

                else -> "$type/${clearPrefixSlash(decodedPath)}"
            }
        }

        private fun getRawBackupFiles(context: Context): List<BackupRawFile> {
            val fileExtensions = BackupFormat.entries.map { it.fileExtension() }

            val backupsUri = Settings.getBackupUri(context) ?: return emptyList()
            if (isTV(context)) {
                val backupsDir = backupsUri.toFile()
                if (backupsDir.exists() && backupsDir.isDirectory) {
                    backupsDir.listFiles()
                    val files = backupsDir.listFiles()?.filter { file ->
                        file.name.startsWith(FILE_NAME_PREFIX) && fileExtensions.any { ext ->
                            file.name.endsWith(
                                ".$ext"
                            )
                        }
                    }
                    return files?.map { BackupRawFile.fromFile(it, context) } ?: emptyList()
                }
            } else {
                val backupsDir = DocumentFile.fromTreeUri(context, backupsUri) ?: return emptyList()
                if (backupsDir.exists() && backupsDir.isDirectory) {
                    val files = backupsDir.listFiles().filter { file ->
                        file.name?.let { name ->
                            name.startsWith(FILE_NAME_PREFIX) && fileExtensions.any { ext ->
                                name.endsWith(
                                    ".$ext"
                                )
                            }
                        } == true
                    }
                    return files.map { BackupRawFile.fromDocumentFile(it, context) }
                }
            }

            return emptyList()
        }

        fun getLastCreatedFileUri(context: Context): Uri? {
            val files = getRawBackupFiles(context)
            if (files.isNotEmpty()) {
                val sortedFiles = files.sortedByDescending { it.lastModified }

                val lastCreatedFile = sortedFiles.firstOrNull()

                if (lastCreatedFile != null) {
                    return lastCreatedFile.uri
                }
            }
            return null
        }

        fun getBackupFiles(context: Context): List<BackupFile> {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault())

            return getRawBackupFiles(context)
                .map { file ->
                    val name = file.name
                    val dateString = name.removePrefix(FILE_NAME_PREFIX).substringBeforeLast('.')
                    val date = dateFormat.parse(dateString) ?: Date()
                    val title = getTitleFromUri(file.uri) ?: name
                    BackupFile(file.uri, date, title)
                }
                .sortedByDescending { it.date }
        }

        fun getTitleFromUri(uri: Uri): String? {
            val pattern =
                Pattern.compile("$FILE_NAME_PREFIX(\\d{4}-\\d{2}-\\d{2}-\\d{2}-\\d{2}-\\d{2})\\.(\\w+)")
            val matcher = pattern.matcher(uri.toString())

            return if (matcher.find()) {
                val dateString = matcher.group(1)
                val extension = matcher.group(2)

                val dateFormat = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault())
                val titleFormatter = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

                val date = dateString?.let { dateFormat.parse(it) }
                if (date != null) {
                    if (extension != null) {
                        val format = extension.let { BackupFormat.fromExtension(it) }
                        "${titleFormatter.format(date)} (${format.value})"
                    } else {
                        titleFormatter.format(date)
                    }
                } else {
                    null
                }
            } else {
                null
            }
        }

        fun run(context: Context, onComplete: ((Uri) -> Unit)? = null) {
            if (onComplete != null) {
                onCompleteCallback = onComplete
            }

            val intent = Intent(context, BackupService::class.java)
            context.startForegroundService(intent)

            val broadcastIntent = Intent("org.androidlabs.applistbackup.BACKUP_ACTION")
            context.sendBroadcast(broadcastIntent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(tag, "start: ${intent.toString()}")

        val source = intent?.getStringExtra("source")
        val inputFormat = intent?.getStringExtra("format")

        serviceScope.launch {
            performBackup(source, inputFormat)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }

        return START_STICKY
    }

    private fun performBackup(source: String?, inputFormat: String?) {
        isRunning.value = true

        val startDate = Date()
        createNotificationChannels()

        val backupsDir = getBackupFolder(this)

        if (backupsDir != null) {
            val appWidgetManager = AppWidgetManager.getInstance(this)
            val thisAppWidget = ComponentName(this.packageName, BackupWidget::class.java.name)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidget)
            for (appWidgetId in appWidgetIds) {
                updateAppWidget(this, appWidgetManager, appWidgetId, showLoading = true)
            }

            val notification = NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
                .setContentTitle(getString(R.string.backup_started))
                .setContentText(getString(R.string.in_progress))
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .build()

            startForeground(1, notification)

            try {
                val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }

                val dateFormat = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault())
                val outputDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

                val formats = if (inputFormat != null) {
                    val values = inputFormat.split(",")
                    val filtered = values.map {
                        try {
                            BackupFormat.fromString(it)
                        } catch (e: Exception) {
                            null
                        }
                    }.filterIsInstance<BackupFormat>()
                        .toSet()
                    if (filtered.isEmpty()) setOf(BackupFormat.HTML) else filtered
                } else {
                    Settings.getBackupFormats(
                        this
                    )
                }

                val excludeItems = Settings.getBackupExcludeData(this)
                val currentDate = Date()
                val currentTime = dateFormat.format(currentDate)
                val type =
                    getString(if (source != null && source == "tasker") R.string.automatic else R.string.manual)

                val isPackageExcluded = excludeItems.contains(BackupAppInfo.Package)
                val isSystemExcluded = excludeItems.contains(BackupAppInfo.System)
                val isEnabledExcluded = excludeItems.contains(BackupAppInfo.Enabled)
                val isVersionExcluded = excludeItems.contains(BackupAppInfo.Version)
                val isTargetSDKExcluded = excludeItems.contains(BackupAppInfo.TargetSDK)
                val isMinSDKExcluded = excludeItems.contains(BackupAppInfo.MinSDK)
                val isInstalledAtExcluded = excludeItems.contains(BackupAppInfo.InstalledAt)
                val isUpdatedAtExcluded = excludeItems.contains(BackupAppInfo.UpdatedAt)
                val isInstallSourceExcluded = excludeItems.contains(BackupAppInfo.InstallSource)
                val isLinksExcluded = excludeItems.contains(BackupAppInfo.Links)

                var systemAppsCount = 0
                var appsCount = 0
                var enabledAppsCount = 0

                val installedPackages =
                    packageManager.getInstalledPackages(PackageManager.GET_META_DATA)
                val activeApps = packageManager.queryIntentActivities(mainIntent, 0)
                    .map { it.activityInfo.packageName }

                val apps: MutableList<BackupAppDetails> = mutableListOf()

                installedPackages.forEach { packageInfo ->
                    val appInfo = packageInfo.applicationInfo ?: return@forEach
                    val isSystem = appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0 ||
                            appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
                    if (isSystem && !activeApps.contains(packageInfo.packageName)) {
                        return@forEach
                    }
                    val packageName = appInfo.packageName
                    if (isSystem) {
                        systemAppsCount++
                    }
                    appsCount++

                    if (appInfo.enabled) {
                        enabledAppsCount++
                    }

                    val installerPackageName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        packageManager.getInstallSourceInfo(packageName).initiatingPackageName
                    } else {
                        packageManager.getInstallerPackageName(packageName)
                    }

                    val installerName = if (installerPackageName != null) {
                        try {
                            val applicationInfo =
                                packageManager.getApplicationInfo(installerPackageName, 0)
                            val appName =
                                packageManager.getApplicationLabel(applicationInfo).toString()
                            appName
                        } catch (e: PackageManager.NameNotFoundException) {
                            installerPackageName
                        }
                    } else {
                        getString(R.string.none)
                    }

                    val appDetails = BackupAppDetails(
                        packageName = appInfo.packageName,
                        name = packageManager.getApplicationLabel(appInfo),
                        icon = packageManager.getApplicationIcon(appInfo),
                        isSystem = isSystem,
                        isEnabled = appInfo.enabled,
                        installerName = installerName,
                        versionName = packageInfo.versionName ?: "",
                        versionCode = packageInfo.longVersionCode,
                        targetSdkVersion = appInfo.targetSdkVersion,
                        minSdkVersion = appInfo.minSdkVersion,
                        firstInstallTime = packageInfo.firstInstallTime,
                        lastUpdateTime = packageInfo.lastUpdateTime,
                    )
                    apps.add(appDetails)
                }

                val userAppsCount = appsCount - systemAppsCount
                val disabledAppsCount = appsCount - enabledAppsCount

                formats.forEach { format ->
                    try {
                        val fileName = "$FILE_NAME_PREFIX$currentTime.${format.fileExtension()}"
                        val newFile = backupsDir.createFile(format.mimeType(), fileName)

                        when (format) {
                            BackupFormat.HTML -> {
                                val template =
                                    assets.open("template.html").bufferedReader()
                                        .use { it.readText() }
                                val appItems = StringBuilder()
                                val installerItems = StringBuilder()
                                val installerFilterItems = StringBuilder()
                                val installerFilterData = StringBuilder()
                                val appsByInstallerCount = mutableMapOf<String, Int>()

                                apps.forEachIndexed { index, app ->
                                    val installerName = app.installerName
                                    appsByInstallerCount[installerName] =
                                        appsByInstallerCount.getOrPut(installerName) { 0 } + 1

                                    appItems.append(
                                        """
    <div class="app-item"
    data-install-time="${app.firstInstallTime}"
    data-update-time="${app.lastUpdateTime}"
    data-app-name="${app.name}"
    data-package-name="$packageName"
    data-is-system-app="${app.isSystem}"
    data-is-enabled="${app.isEnabled}"
    data-installer="$installerName"
    data-default-order="$index">
        <img src="${drawableToBase64(app.icon)}" alt="${app.name}">
        <div class="app-details">
            <strong class="app-name">${app.name}</strong><br>
            ${if (!isPackageExcluded) "<strong>${getString(R.string.package_title)}:</strong> $packageName<br>" else ""}
            ${if (!isSystemExcluded) "<strong>${getString(R.string.system_title)}:</strong> ${app.isSystem}<br>" else ""}
            ${if (!isEnabledExcluded) "<strong>${getString(R.string.enabled_title)}:</strong> ${app.isEnabled}<br>" else ""}
            ${if (!isVersionExcluded) "<strong>${getString(R.string.version_title)}:</strong> ${app.versionName} (${app.versionCode})<br>" else ""}
            ${if (!isTargetSDKExcluded) "<strong>${getString(R.string.target_sdk_version_title)}:</strong> ${app.targetSdkVersion}<br>" else ""}
            ${if (!isMinSDKExcluded) "<strong>${getString(R.string.min_sdk_version_title)}:</strong> ${app.minSdkVersion}<br>" else ""}
            ${
                                            if (!isInstalledAtExcluded) "<strong>${getString(R.string.installed_at_title)}:</strong> ${
                                                outputDateFormat.format(
                                                    Date(app.firstInstallTime)
                                                )
                                            }<br>" else ""
                                        }
            ${
                                            if (!isUpdatedAtExcluded) "<strong>${getString(R.string.updated_at_title)}:</strong> ${
                                                outputDateFormat.format(
                                                    Date(app.lastUpdateTime)
                                                )
                                            }<br>" else ""
                                        }
            ${if (!isInstallSourceExcluded) "<strong>${getString(R.string.installer)}:</strong> $installerName<br>" else ""}
            ${
                                            if (!isLinksExcluded) """
                <strong>${getString(R.string.links_title)}</strong> (${getString(R.string.links_title_details)}):<br>
                <a target="_blank" rel="noopener noreferrer" href="https://play.google.com/store/apps/details?id=$packageName">Play Market</a> | 
                <a target="_blank" rel="noopener noreferrer" href="https://f-droid.org/packages/$packageName">F-Droid</a>
            """.trimIndent() else ""
                                        }
        </div>
    </div>
""".trimIndent()
                                    )
                                }

                                appsByInstallerCount.forEach { (installer, count) ->
                                    val filterId = "filter-installer-${
                                        installer.lowercase().replace("\\s".toRegex(), "-")
                                    }"

                                    installerFilterData.append("\"$filterId\":\"$installer\",")

                                    installerFilterItems.append(
                                        """
                        <label>
                            <input type="checkbox" id="$filterId" checked> ${getString(R.string.installed_from)} $installer
                        </label>
                        """.trimIndent()
                                    )

                                    installerItems.append(
                                        """
                        <div class="stat-item">
                            <b>$installer</b>
                            <p>$count</p>
                       </div>
                        """.trimIndent()
                                    )
                                }

                                val durationMillis = Date().time - startDate.time
                                val durationSeconds = durationMillis / 1000.0
                                val decimalFormat =
                                    DecimalFormat("0.000 ${getString(R.string.seconds)}")
                                val formattedDuration = decimalFormat.format(durationSeconds)

                                val placeholders = mapOf(
                                    "APP_ITEMS_PLACEHOLDER" to appItems.toString(),
                                    "INSTALLERS_STATISTICS" to installerItems.toString(),
                                    "INSTALLERS_FILTERS_DATA" to installerFilterData.toString(),
                                    "INSTALLERS_FILTERS" to installerFilterItems.toString(),
                                    "BACKUP_TIME_PLACEHOLDER" to outputDateFormat.format(currentDate),
                                    "TRIGGER_TYPE_PLACEHOLDER" to type,
                                    "TOTAL_APPS_COUNT_PLACEHOLDER" to appsCount.toString(),
                                    "USER_APPS_COUNT_PLACEHOLDER" to userAppsCount.toString(),
                                    "SYSTEM_APPS_COUNT_PLACEHOLDER" to systemAppsCount.toString(),
                                    "ENABLED_APPS_COUNT_PLACEHOLDER" to enabledAppsCount.toString(),
                                    "DISABLED_APPS_COUNT_PLACEHOLDER" to disabledAppsCount.toString(),
                                    "BACKUP_DURATION_PLACEHOLDER" to formattedDuration,

                                    "LOCALISATION_CREATED_AT" to getString(R.string.created_at),
                                    "LOCALISATION_TRIGGER_TYPE" to getString(R.string.trigger_type),
                                    "LOCALISATION_TOTAL_APPS_COUNT" to getString(R.string.total_apps_count),
                                    "LOCALISATION_USER_APPS_COUNT" to getString(R.string.user_apps_count),
                                    "LOCALISATION_SYSTEM_APPS_COUNT" to getString(R.string.system_apps_count),
                                    "LOCALISATION_ENABLED_APPS_COUNT" to getString(R.string.enabled_apps_count),
                                    "LOCALISATION_DISABLED_APPS_COUNT" to getString(R.string.disabled_apps_count),
                                    "LOCALISATION_INSTALLED_APPS_COUNT" to getString(R.string.installed_apps_count),
                                    "LOCALISATION_UNINSTALLED_APPS_COUNT" to getString(R.string.uninstalled_apps_count),
                                    "LOCALISATION_SEARCH_PLACEHOLDER" to getString(R.string.search_placeholder),
                                    "LOCALISATION_SORT_OPTIONS" to getString(R.string.sort_options),
                                    "LOCALISATION_FILTER_OPTIONS" to getString(R.string.filter_options),
                                    "LOCALISATION_NO_ITEMS_PLACEHOLDER" to getString(R.string.no_items_placeholder),
                                    "LOCALISATION_SORTING" to getString(R.string.sorting),
                                    "LOCALISATION_SORT_BY_DEFAULT" to getString(R.string.sort_by_default),
                                    "LOCALISATION_SORT_BY_INSTALL_TIME" to getString(R.string.sort_by_install_time),
                                    "LOCALISATION_SORT_BY_UPDATE_TIME" to getString(R.string.sort_by_update_time),
                                    "LOCALISATION_SORT_BY_APP_NAME" to getString(R.string.sort_by_app_name),
                                    "LOCALISATION_SORT_BY_PACKAGE_NAME" to getString(R.string.sort_by_package_name),
                                    "LOCALISATION_ORDER" to getString(R.string.order),
                                    "LOCALISATION_ORDER_ASCENDING" to getString(R.string.order_ascending),
                                    "LOCALISATION_ORDER_DESCENDING" to getString(R.string.order_descending),
                                    "LOCALISATION_CLOSE_BUTTON" to getString(R.string.close),
                                    "LOCALISATION_APPS_FILTERING" to getString(R.string.apps_filtering),
                                    "LOCALISATION_USER_APPS" to getString(R.string.user_apps),
                                    "LOCALISATION_SYSTEM_APPS" to getString(R.string.system_apps),
                                    "LOCALISATION_ENABLED_APPS" to getString(R.string.enabled_apps),
                                    "LOCALISATION_DISABLED_APPS" to getString(R.string.disabled_apps),
                                    "LOCALISATION_INSTALLED_APPS" to getString(R.string.installed_apps),
                                    "LOCALISATION_APPLY_FILTERS_BUTTON" to getString(R.string.apply_filters_button),
                                    "LOCALISATION_BACKUP_DURATION" to getString(R.string.backup_duration),
                                    "LOCALISATION_SORT_BY_INSTALLER" to getString(R.string.sort_by_installer_name),
                                    "LOCALISATION_SHOW_MORE" to getString(R.string.show_more),
                                    "LOCALISATION_SHOW_LESS" to getString(R.string.show_less),
                                    "LOCALISATION_INSTALL_SOURCE" to getString(R.string.installer),
                                    "LOCALISATION_APP_STATES" to getString(R.string.app_states),
                                    "LOCALISATION_BACKUP_APPS" to getString(R.string.backup_apps),
                                )

                                var finalHtml = template

                                placeholders.forEach { (placeholder, value) ->
                                    finalHtml = finalHtml.replace("<!-- $placeholder -->", value)
                                }

                                if (newFile != null) {
                                    contentResolver.openOutputStream(newFile.uri)
                                        ?.use { outputStream ->
                                            outputStream.write(finalHtml.toByteArray())
                                        }
                                }
                            }

                            BackupFormat.CSV -> {
                                val csvBuilder = StringBuilder()

                                csvBuilder.apply {
                                    append("\"${getString(R.string.name_title)}\"")
                                    if (!isPackageExcluded) append(",\"${getString(R.string.package_title)}\"")
                                    if (!isSystemExcluded) append(",\"${getString(R.string.system_title)}\"")
                                    if (!isEnabledExcluded) append(",\"${getString(R.string.enabled_title)}\"")
                                    if (!isVersionExcluded) append(",\"${getString(R.string.version_title)}\"")
                                    if (!isTargetSDKExcluded) append(",\"${getString(R.string.target_sdk_version_title)}\"")
                                    if (!isMinSDKExcluded) append(",\"${getString(R.string.min_sdk_version_title)}\"")
                                    if (!isInstalledAtExcluded) append(",\"${getString(R.string.installed_at_title)}\"")
                                    if (!isUpdatedAtExcluded) append(",\"${getString(R.string.updated_at_title)}\"")
                                    if (!isInstallSourceExcluded) append(",\"${getString(R.string.installer)}\"")
                                    if (!isLinksExcluded) {
                                        append(",\"${getString(R.string.play_market_title)}\"")
                                        append(",\"${getString(R.string.f_droid_title)}\"")
                                    }
                                    appendLine()
                                }

                                apps.forEach { app ->
                                    csvBuilder.apply {
                                        append("\"${app.name.toString().replace("\"", "\"\"")}\"")
                                        if (!isPackageExcluded) append(",\"${app.packageName}\"")
                                        if (!isSystemExcluded) append(",\"${app.isSystem}\"")
                                        if (!isEnabledExcluded) append(",\"${app.isEnabled}\"")
                                        if (!isVersionExcluded) append(
                                            ",\"${
                                                app.versionName.replace(
                                                    "\"",
                                                    "\"\""
                                                )
                                            } (${app.versionCode})\""
                                        )
                                        if (!isTargetSDKExcluded) append(",\"${app.targetSdkVersion}\"")
                                        if (!isMinSDKExcluded) append(",\"${app.minSdkVersion}\"")
                                        if (!isInstalledAtExcluded) append(
                                            ",\"${
                                                outputDateFormat.format(
                                                    Date(app.firstInstallTime)
                                                )
                                            }\""
                                        )
                                        if (!isUpdatedAtExcluded) append(
                                            ",\"${
                                                outputDateFormat.format(
                                                    Date(
                                                        app.lastUpdateTime
                                                    )
                                                )
                                            }\""
                                        )
                                        if (!isInstallSourceExcluded) append(
                                            ",\"${
                                                app.installerName.replace(
                                                    "\"",
                                                    "\"\""
                                                )
                                            }\""
                                        )
                                        if (!isLinksExcluded) {
                                            append(",\"https://play.google.com/store/apps/details?id=${app.packageName}\"")
                                            append(",\"https://f-droid.org/packages/${app.packageName}\"")
                                        }
                                        appendLine()
                                    }
                                }

                                if (newFile != null) {
                                    contentResolver.openOutputStream(newFile.uri)
                                        ?.use { outputStream ->
                                            outputStream.write(csvBuilder.toString().toByteArray())
                                        }
                                }
                            }

                            BackupFormat.Markdown -> {
                                val markdownBuilder = StringBuilder()

                                markdownBuilder.apply {
                                    append("**${getString(R.string.name_title)}")
                                    if (!isPackageExcluded) append(" | ${getString(R.string.package_title)}")
                                    if (!isSystemExcluded) append(" | ${getString(R.string.system_title)}")
                                    if (!isEnabledExcluded) append(" | ${getString(R.string.enabled_title)}")
                                    if (!isVersionExcluded) append(" | ${getString(R.string.version_title)}")
                                    if (!isTargetSDKExcluded) append(" | ${getString(R.string.target_sdk_version_title)}")
                                    if (!isMinSDKExcluded) append(" | ${getString(R.string.min_sdk_version_title)}")
                                    if (!isInstalledAtExcluded) append(" | ${getString(R.string.installed_at_title)}")
                                    if (!isUpdatedAtExcluded) append(" | ${getString(R.string.updated_at_title)}")
                                    if (!isInstallSourceExcluded) append(" | ${getString(R.string.installer)}")
                                    if (!isLinksExcluded) append(" | ${getString(R.string.links_title)}")
                                    append("**")
                                    appendLine()
                                    appendLine()
                                }

                                apps.forEach { app ->
                                    markdownBuilder.apply {
                                        append("**${app.name}**")
                                        if (!isPackageExcluded) append(" | ${app.packageName}")
                                        if (!isSystemExcluded) append(" | ${app.isSystem}")
                                        if (!isEnabledExcluded) append(" | ${app.isEnabled}")
                                        if (!isVersionExcluded) append(" | ${app.versionName} (${app.versionCode})")
                                        if (!isTargetSDKExcluded) append(" | ${app.targetSdkVersion}")
                                        if (!isMinSDKExcluded) append(" | ${app.minSdkVersion}")
                                        if (!isInstalledAtExcluded) append(
                                            " | ${
                                                outputDateFormat.format(
                                                    Date(app.firstInstallTime)
                                                )
                                            }"
                                        )
                                        if (!isUpdatedAtExcluded) append(
                                            " | ${
                                                outputDateFormat.format(
                                                    Date(
                                                        app.lastUpdateTime
                                                    )
                                                )
                                            }"
                                        )
                                        if (!isInstallSourceExcluded) append(" | ${app.installerName}")
                                        if (!isLinksExcluded) append(" | [Play](https://play.google.com/store/apps/details?id=${app.packageName}) | [F-Droid](https://f-droid.org/packages/${app.packageName})")

                                        appendLine()
                                        appendLine()
                                    }
                                }

                                if (newFile != null) {
                                    contentResolver.openOutputStream(newFile.uri)
                                        ?.use { outputStream ->
                                            outputStream.write(
                                                markdownBuilder.toString().toByteArray()
                                            )
                                        }
                                }
                            }
                        }

                        if (newFile == null) {
                            throw Error(getString(R.string.file_create_failed))
                        }


                        val mainActivityIntent = Intent(this, MainActivity::class.java).apply {
                            putExtra("uri", newFile.uri.toString())
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }

                        val notificationId = getNotificationId()

                        val pendingIntent = PendingIntent.getActivity(
                            this,
                            notificationId,
                            mainActivityIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )

                        val successfulTitle =
                            getString(R.string.backup_done_title, appsCount.toString(), type)
                        val successfulText = getString(
                            R.string.backup_done_text,
                            userAppsCount.toString(),
                            systemAppsCount.toString()
                        )

                        val endNotification = NotificationCompat.Builder(this, BACKUP_CHANNEL_ID)
                            .setContentTitle(successfulTitle)
                            .setContentText(successfulText)
                            .setSmallIcon(R.drawable.ic_launcher_foreground)
                            .setContentIntent(pendingIntent)
                            .build()

                        val manager = getSystemService(NotificationManager::class.java)
                        manager.notify(notificationId, endNotification)

                        if (onCompleteCallback != null) {
                            onCompleteCallback?.let { it(newFile.uri) }
                            onCompleteCallback = null
                        }
                    } catch (exception: Exception) {
                        val endNotification = NotificationCompat.Builder(this, BACKUP_CHANNEL_ID)
                            .setContentTitle(getString(R.string.backup_failed))
                            .setContentText(exception.localizedMessage)
                            .setSmallIcon(R.drawable.ic_launcher_foreground)
                            .build()

                        val manager = getSystemService(NotificationManager::class.java)
                        manager.notify(getNotificationId(), endNotification)
                    }
                }
            } catch (exception: Exception) {
                val endNotification = NotificationCompat.Builder(this, BACKUP_CHANNEL_ID)
                    .setContentTitle(getString(R.string.backup_failed))
                    .setContentText(exception.localizedMessage)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .build()

                val manager = getSystemService(NotificationManager::class.java)
                manager.notify(getNotificationId(), endNotification)
            }

            Log.d(tag, "end")

            for (appWidgetId in appWidgetIds) {
                updateAppWidget(this, appWidgetManager, appWidgetId, showLoading = false)
            }
        } else {
            Log.d(tag, "failed due no destination")

            val endNotification = NotificationCompat.Builder(this, BACKUP_CHANNEL_ID)
                .setContentTitle(getString(R.string.backup_failed))
                .setContentText(getString(R.string.destination_not_set_notification))
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .build()

            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(getNotificationId(), endNotification)
        }

        isRunning.value = false
    }

    private fun getNotificationId(): Int {
        return (System.currentTimeMillis() and 0xfffffff).toInt()
    }

    private fun drawableToBase64(drawable: Drawable): String {
        val bitmap = if (drawable is BitmapDrawable) {
            drawable.bitmap
        } else {
            createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight).apply {
                val canvas = Canvas(this)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
            }
        }

        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()

        return "data:image/png;base64, " + Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    private fun createNotificationChannels() {
        val foregroundServiceChannel = NotificationChannel(
            SERVICE_CHANNEL_ID,
            "Backup Service Notification",
            NotificationManager.IMPORTANCE_DEFAULT
        )

        // Channel for End Notifications
        val endNotificationChannel = NotificationChannel(
            BACKUP_CHANNEL_ID,
            "Backup Notification",
            NotificationManager.IMPORTANCE_HIGH
        )

        // Register both channels with the system
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(foregroundServiceChannel)
        manager.createNotificationChannel(endNotificationChannel)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        Log.d(tag, "destroy")
    }
}