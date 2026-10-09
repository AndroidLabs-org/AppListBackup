package org.androidlabs.applistbackup

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.text.TextUtils
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.ContentResolver
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
import android.system.Os
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
import org.androidlabs.applistbackup.data.BackupApp
import org.androidlabs.applistbackup.data.BackupAppDetails
import org.androidlabs.applistbackup.data.BackupAppInfo
import org.androidlabs.applistbackup.data.BackupFile
import org.androidlabs.applistbackup.data.BackupFormat
import org.androidlabs.applistbackup.data.BackupFormatResult
import org.androidlabs.applistbackup.data.BackupRawFile
import org.androidlabs.applistbackup.data.EnvironmentInfoProvider
import org.androidlabs.applistbackup.data.FileInfo
import org.androidlabs.applistbackup.data.SortBy
import org.androidlabs.applistbackup.data.SortOrder
import org.androidlabs.applistbackup.data.toHtml
import org.androidlabs.applistbackup.data.toMarkdown
import org.androidlabs.applistbackup.settings.Settings
import org.androidlabs.applistbackup.utils.Utils.clearPrefixSlash
import org.androidlabs.applistbackup.utils.Utils.isTV
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern
import androidx.core.graphics.scale

class BackupService : Service() {
    private val tag: String = "BackupService"

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    companion object {
        const val SERVICE_CHANNEL_ID = "BackupService"
        const val BACKUP_CHANNEL_ID = "Backup"

        const val FILE_NAME_PREFIX = "app-list-backup"

        private val backupNameRegex = Regex(
            "^app-list-backup-\\d{4}-\\d{2}-\\d{2}-\\d{2}-\\d{2}-\\d{2}\\.(html|csv|md)$",
            RegexOption.IGNORE_CASE
        )
        val isRunning = MutableStateFlow(false)

        private var onCompleteCallback: ((uri: Uri?, temp: Boolean) -> Unit)? = null

        private val backupDateFormat = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US)

        fun getBackupFolder(context: Context): BackupRawFile? {
            val backupsUri = Settings.getBackupUri(context) ?: return null
            if (isTV(context)) {
                return BackupRawFile.fromFile(backupsUri.toFile(), context)
            } else {
                val doc = DocumentFile.fromTreeUri(context, backupsUri) ?: return null
                if (!doc.exists()) return null
                return BackupRawFile.fromDocumentFile(doc, context)
            }
        }

        fun getReadablePathFromUri(context: Context, uri: Uri?): String {
            var decodedPath = ""
            if (uri == null) {
                return decodedPath
            }

            val providerName = try {
                val authority = uri.authority
                val providerInfo = context.packageManager.resolveContentProvider(authority ?: "", 0)
                val appInfo = providerInfo?.applicationInfo
                appInfo?.loadLabel(context.packageManager)?.toString()
            } catch (e: Exception) {
                null
            }

            var type = "raw"
            var docId = ""

            if (isTV(context)) {
                decodedPath = uri.path!!
            } else {
                docId = DocumentsContract.getTreeDocumentId(uri)
                val split = docId.split(":")
                type = split[0]
                val path = split.getOrNull(1) ?: ""
                decodedPath = URLDecoder.decode(path, StandardCharsets.UTF_8.toString())
            }

            val basePath = when (type) {
                "primary" -> clearPrefixSlash(decodedPath)
                "home" -> "Home/${clearPrefixSlash(decodedPath)}"
                "raw" -> {
                    val internalPath = Environment.getExternalStorageDirectory().path
                    when {
                        decodedPath.startsWith(internalPath) -> {
                                clearPrefixSlash(
                                    decodedPath.removePrefix(
                                        internalPath
                                    )
                                )
                        }

                        else -> {
                            decodedPath
                        }
                    }
                }

                // A cloud provider's own doc ID (Drive, OneDrive, …) doesn't follow the
                // authority:path convention local/SD storage uses — it's an opaque,
                // provider-specific identifier with no path segment to decode, so this
                // used to display it raw (e.g. "acc=1;doc=encoded=9VKp7q32…"). Every
                // DocumentsProvider is required to expose a real display name; ask for
                // that instead of trying to parse a path out of an ID that has none.
                else -> DocumentFile.fromTreeUri(context, uri)?.name ?: docId
            }

            return when {
                providerName != null -> "$providerName/${basePath.removePrefix("./")}"
                else -> basePath
            }
        }

        private fun getRawBackupFiles(context: Context): List<BackupRawFile> {

            val backupsUri = Settings.getBackupUri(context) ?: return emptyList()

            return if (isTV(context)) {
                val backupsDir = backupsUri.toFile()
                if (!backupsDir.exists() || !backupsDir.isDirectory) return emptyList()
                val files = backupsDir.listFiles()
                    ?.filter { file ->
                        isValidBackupName(file.name)
                    }
                    ?: emptyList()
                files
                    .map { BackupRawFile.fromFile(it, context) }
                    .sortedByDescending { it.lastModified }

            } else {
                val backupsDir = DocumentFile.fromTreeUri(context, backupsUri)
                    ?: return emptyList()
                if (!backupsDir.exists() || !backupsDir.isDirectory) return emptyList()
                val files = backupsDir.listFiles()
                    .filter { file ->
                        val name = file.name ?: return@filter false
                        isValidBackupName(name)
                    }
                files
                    .map { BackupRawFile.fromDocumentFile(it, context) }
                    .sortedByDescending { it.lastModified }


            }
        }
        fun getLastCreatedFileUri(context: Context): Uri? {
            val files = getRawBackupFiles(context)
            val lastCreatedFile = files.firstOrNull()
            if (lastCreatedFile != null) {
                return lastCreatedFile.uri
            }
            return null
        }

        private fun isValidBackupName(name: String): Boolean {
            return backupNameRegex.matches(name)
        }
        fun getBackupFiles(context: Context): List<BackupFile> {
            val files = getRawBackupFiles(context)
            val infos = files.mapNotNull { file ->
                getFileInfoFromUri(context, file.uri)
            }


            val filesByGeneration = infos
                .groupBy { backupDateFormat.format(it.date) }
                .toSortedMap(reverseOrder())

            val generationsCount = filesByGeneration.size

            return filesByGeneration.values.flatMapIndexed { generationNumber, filesInGeneration ->
                filesInGeneration.map { info ->
                    BackupFile(
                        info.uri,
                        info.date,
                        info.name,
                        generationNumber,
                        generationsCount
                    )
                }
            }.sortedByDescending { it.date }
        }

        fun getFileInfoFromUri(context: Context, uri: Uri): FileInfo? {

            val pattern = Pattern.compile(
                "^$FILE_NAME_PREFIX(?:-(\\d{4}-\\d{2}-\\d{2}-\\d{2}-\\d{2}-\\d{2}))?\\.(\\w+)$",
                Pattern.CASE_INSENSITIVE
            )

            val fileName = when (uri.scheme) {
                ContentResolver.SCHEME_FILE ->
                    File(uri.path ?: "").name

                else ->
                    DocumentFile.fromSingleUri(context, uri)?.name
            } ?: return null

            val matcher = pattern.matcher(fileName)

            if (!matcher.matches()) {
                return null
            }

            val dateString = matcher.group(1)
            val extension = matcher.group(2)?.lowercase()

            val format = extension
                ?.let { BackupFormat.fromExtension(it) }
                ?: return null

            val titleFormatter = SimpleDateFormat(
                "MMM dd, yyyy HH:mm",
                Locale.getDefault()
            )

            val date = if (dateString != null) {
                try {
                    backupDateFormat.parse(dateString) ?: return null
                } catch (e: Exception) {
                    Log.e("BackupService", "Error parsing date: ${e.message}")
                    return null
                }
            } else {
                Date(getFileDate(context, uri))
            }

            return FileInfo(
                uri,
                "${titleFormatter.format(date)} (${format.value})",
                format.value,
                date
            )
        }

        fun run(context: Context, temporary: Boolean, onComplete: ((uri: Uri?, temp: Boolean) -> Unit)? = null) {
            if (onComplete != null) {
                onCompleteCallback = onComplete
            }

            val intent = Intent(context, BackupService::class.java)
            intent.putExtra("temporary",temporary)
            context.startForegroundService(intent)
        }

        private fun getFileDate(context: Context, uri: Uri): Long {
            try {
                when (uri.scheme) {
                    ContentResolver.SCHEME_CONTENT -> {
                        context.contentResolver.query(
                            uri,
                            arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED),
                            null,
                            null,
                            null
                        )?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val lastModifiedIndex = cursor.getColumnIndex(
                                    DocumentsContract.Document.COLUMN_LAST_MODIFIED
                                )
                                if (lastModifiedIndex != -1) {
                                    return cursor.getLong(lastModifiedIndex)
                                }
                            }
                        }

                        val fileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
                        fileDescriptor?.use {
                            val stat = Os.fstat(it.fileDescriptor)
                            return stat.st_mtime * 1000L
                        }
                    }

                    ContentResolver.SCHEME_FILE -> {
                        val file = File(uri.path ?: "")
                        if (file.exists()) {
                            return file.lastModified()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("BackupService", "Error getting file date: ${e.message}")
            }

            return System.currentTimeMillis()
        }

        // A BroadcastReceiver (or the Tasker/Locale plugin's runner, which executes with no
        // guaranteed foreground state) may not call startForegroundService(): the process sits
        // in a background state and Android answers ForegroundServiceStartNotAllowedException,
        // taking the whole app down on every scripted trigger. performBackup takes an explicit
        // Context and an optional promoteForeground callback so BackupWorker can run the exact
        // same logic without ever trying to become a foreground service - the backup itself
        // takes about a second, so WorkManager's own execution window is enough on its own.
        internal fun performBackup(
            context: Context,
            source: String?,
            inputFormat: String?,
            temporary: Boolean,
            promoteForeground: ((Int, Notification) -> Unit)? = null
        ) {
            with(context) {
                isRunning.value = true
                BackupTempStore.clear()
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

                    promoteForeground?.invoke(1, notification)

                    try {

                        val dateFormat = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault())
                        val outputDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

                        val formats = inputFormat
                            ?.split(",")
                            ?.mapNotNull { BackupFormat.fromString(it.trim()) }
                            ?.toSet()
                            ?.ifEmpty { setOf(BackupFormat.HTML) }
                            ?: Settings.getBackupFormats(this)

                        val backupApps = Settings.getBackupApps(this)
                        val excludeItems = Settings.getBackupExcludeData(this)
                        val currentDate = Date()
                        val currentTime = dateFormat.format(currentDate)
                        val createLatestBackup = Settings.getCreateLatestBackup(this)
                        val backupSortBy = Settings.getBackupSortBy(this)
                        val backupSortOrder = Settings.getBackupSortOrder(this)
                        val includeSystemInfo = Settings.getIncludeSystemInfo(this)
                        val backupEnvironmentInfo = EnvironmentInfoProvider.collect(this)

                        val type =
                            getString(
                                // Any source at all means the run was triggered from outside
                                // the UI. Matching only "tasker" labelled every broadcast- and
                                // widget-driven backup as Manual.
                                if (source != null) R.string.automatic else R.string.manual
                            )

                        val isPackageExcluded = excludeItems.contains(BackupAppInfo.Package)
                        // Every icon is embedded as a base64 PNG - by far the biggest
                        // contributor to HTML backup size (roughly 20x a CSV of the same
                        // apps). Excluding it is the size fix; nothing else here is.
                        val isIconExcluded = excludeItems.contains(BackupAppInfo.Icon)
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
                        var disabledAppsCount = 0


                        val installedPackages =
                            packageManager.getInstalledPackages(PackageManager.GET_META_DATA)

                        val excludedPackages = Settings.getBackupExcludedPackages(this)

                        val apps: MutableList<BackupAppDetails> = mutableListOf()

                        installedPackages.forEach { packageInfo ->
                            val appInfo = packageInfo.applicationInfo ?: return@forEach
                            val isSystem = appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0 ||
                                    appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
                            val packageName = appInfo.packageName

                            if (excludedPackages.contains(packageName)) {
                                return@forEach
                            }

                            val installerPackageName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                packageManager.getInstallSourceInfo(packageName).initiatingPackageName
                            } else {
                                @Suppress("DEPRECATION")
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

                            val enabledState = packageManager.getApplicationEnabledSetting(appInfo.packageName)

                            val isEnabled = when (enabledState) {
                                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
                                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> false
                                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                                // COMPONENT_ENABLED_STATE_DEFAULT means "whatever the manifest
                                // says", which for a preinstalled app is often disabled.
                                // Treating it as enabled mislabelled those packages and let
                                // them slip past the disabled filter — issues #4 and #70.
                                else -> appInfo.enabled
                            }

                            val appDetails = BackupAppDetails(
                                packageName = appInfo.packageName,
                                name = packageManager.getApplicationLabel(appInfo),
                                icon = packageManager.getApplicationIcon(appInfo),
                                isSystem = isSystem,
                                isEnabled = isEnabled,
                                installerName = installerName,
                                versionName = packageInfo.versionName ?: "",
                                versionCode = packageInfo.longVersionCode,
                                targetSdkVersion = appInfo.targetSdkVersion,
                                minSdkVersion = appInfo.minSdkVersion,
                                firstInstallTime = packageInfo.firstInstallTime,
                                lastUpdateTime = packageInfo.lastUpdateTime,
                            )

                            val isUser = !isSystem
                            val isDisabled = !isEnabled

                            val shouldInclude =
                                ( (BackupApp.USER in backupApps && isUser) ||
                                  (BackupApp.SYSTEM in backupApps && isSystem)
                                ) && (
                                  BackupApp.DISABLED in backupApps || !isDisabled
                                )

                            // Count only what actually lands in the backup, so the report summary and
                            // the completion notification describe the file rather than the device.
                            if (shouldInclude) {
                                apps.add(appDetails)

                                appsCount++
                                if (isSystem) systemAppsCount++
                                if (isEnabled) enabledAppsCount++ else disabledAppsCount++
                            }
                        }

                        when (backupSortBy) {
                            SortBy.APP_NAME -> {
                                if (backupSortOrder == SortOrder.ASCENDING) {
                                    apps.sortBy { it.name.toString().lowercase(Locale.getDefault()) }
                                } else {
                                    apps.sortByDescending { it.name.toString().lowercase(Locale.getDefault()) }
                                }
                            }

                            SortBy.INSTALL_TIME -> {
                                if (backupSortOrder == SortOrder.ASCENDING) {
                                    apps.sortBy { it.firstInstallTime }
                                } else {
                                    apps.sortByDescending { it.firstInstallTime }
                                }
                            }
                        }

                        val userAppsCount = appsCount - systemAppsCount

                        val results: MutableList<BackupFormatResult> = mutableListOf()

                        // Clear every slot this backup is about to occupy in one directory listing.
                        // Doing it per file meant a Storage Access Framework round trip per name —
                        // six of them for a three-format backup, which measured twenty times the cost
                        // of a single format rather than the expected three.
                        if (!temporary) {
                            val toClear = mutableSetOf<String>()
                            formats.forEach { format ->
                                toClear += "$FILE_NAME_PREFIX-$currentTime.${format.fileExtension()}"
                                toClear += "$FILE_NAME_PREFIX.${format.fileExtension()}"
                            }
                            backupsDir.deleteFiles(toClear)
                        }

                        formats.forEach { format ->
                            try {

                                val suffix = if (temporary) "temp" else currentTime
                                val fileName = "$FILE_NAME_PREFIX-$suffix.${format.fileExtension()}"
                                val newFile =
                                    if (temporary) {
                                        null
                                    } else {
                                        // The slot was cleared above, so createFile cannot fall back to
                                        // inventing "… (1).html" — a name the listing pattern rejects,
                                        // which would leave a file the app can neither show nor apply
                                        // the retention limit to.
                                        backupsDir.createFile(format.mimeType(), fileName)
                                    }

                                val latestName = "$FILE_NAME_PREFIX.${format.fileExtension()}"

                                val latestFile =
                                    if (temporary || !createLatestBackup) {
                                        null
                                    } else {
                                        backupsDir.createFile(
                                            format.mimeType(),
                                            latestName
                                        )
                                    }

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

                                            // Escaped: an app can set its own label (and, via a
                                            // rogue installer, the installer name) to arbitrary
                                            // text. Unescaped, a label containing a quote or angle
                                            // bracket breaks out of the attribute or tag it lands
                                            // in and can inject markup into every report a victim
                                            // opens.
                                            val safeAppName = TextUtils.htmlEncode(app.name.toString())
                                            val safeInstallerName = TextUtils.htmlEncode(installerName)

                                            appItems.append(
                                                """
                                                    <div class="app-item"
                                                    data-install-time="${app.firstInstallTime}"
                                                    data-update-time="${app.lastUpdateTime}"
                                                    data-app-name="$safeAppName"
                                                    data-package-name="${app.packageName}"
                                                    data-is-system-app="${app.isSystem}"
                                                    data-is-enabled="${app.isEnabled}"
                                                    data-installer="$safeInstallerName"
                                                    data-default-order="$index">
                                                        ${if (!isIconExcluded) "<img src=\"${drawableToBase64(app.icon)}\" alt=\"$safeAppName\">" else ""}
                                                        <div class="app-details">
                                                            <strong class="app-name">$safeAppName</strong><br>
                                                            ${if (!isPackageExcluded) "<strong>${getString(R.string.package_title)}:</strong> ${app.packageName}<br>" else ""}
                                                            ${if (!isSystemExcluded) "<div class=\"field-system\"><strong>${getString(R.string.system_title)}:</strong> ${app.isSystem}</div>" else ""}
                                                            ${if (!isEnabledExcluded) "<div class=\"field-enabled\"><strong>${getString(R.string.enabled_title)}:</strong> ${app.isEnabled}</div>" else ""}
                                                            ${if (!isVersionExcluded) "<div class=\"field-version\"><strong>${getString(R.string.version_title)}:</strong> ${app.versionName} (${app.versionCode})</div>" else ""}
                                                            ${if (!isTargetSDKExcluded) "<div class=\"field-target-sdk\"><strong>${getString(R.string.target_sdk_version_title)}:</strong> ${app.targetSdkVersion}</div>" else ""}
                                                            ${if (!isMinSDKExcluded) "<div class=\"field-min-sdk\"><strong>${getString(R.string.min_sdk_version_title)}:</strong> ${app.minSdkVersion}</div>" else ""}
                                                            ${
                                                                                            if (!isInstalledAtExcluded) "<div class=\"field-installed-at\"><strong>${getString(R.string.installed_at_title)}:</strong>${
                                                                                                outputDateFormat.format(
                                                                                                    Date(app.firstInstallTime)
                                                                                                )
                                                                                            }</div>" else ""
                                                                                        }
                                                            ${
                                                                                            if (!isUpdatedAtExcluded) "<div class=\"field-last-update\"><strong>${getString(R.string.updated_at_title)}:</strong> ${
                                                                                                outputDateFormat.format(
                                                                                                    Date(app.lastUpdateTime)
                                                                                                )
                                                                                            }</div>" else ""
                                                                                        }
                                                            ${if (!isInstallSourceExcluded) "<div class=\"field-install-source\"><strong>${getString(R.string.installer)}:</strong> $safeInstallerName</div>" else ""}
                                                            ${
                                                                                            if (!isLinksExcluded) """
                                                                <div class="field-links"><strong>${getString(R.string.links_title)}</strong> (${getString(R.string.links_title_details)}):
                                                                <a target="_blank" rel="noopener noreferrer" href="https://play.google.com/store/apps/details?id=${app.packageName}">Play Market</a> |
                                                                <a target="_blank" rel="noopener noreferrer" href="https://f-droid.org/packages/${app.packageName}">F-Droid</a></div>
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

                                            val safeInstaller = TextUtils.htmlEncode(installer)
                                            installerFilterData.append("\"$filterId\":\"$safeInstaller\",")

                                            installerFilterItems.append(
                                                """
                                <label>
                                    <input type="checkbox" id="$filterId" checked> ${getString(R.string.installed_from)} $safeInstaller
                                </label>
                                """.trimIndent()
                                            )

                                            installerItems.append(
                                                """
                                <div class="stat-item">
                                    <b>$safeInstaller</b>
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


                                        val environmentHtml =
                                            if (includeSystemInfo) {
                                                backupEnvironmentInfo.toHtml()
                                            } else {
                                                ""
                                            }

                                        // Only offer a toggle for a field that is actually
                                        // present. An excluded field is absent from every
                                        // entry, so its control would do nothing at all.
                                        val fieldToggles = listOf(
                                            "system" to (BackupAppInfo.System to R.string.system_title),
                                            "enabled" to (BackupAppInfo.Enabled to R.string.enabled_title),
                                            "version" to (BackupAppInfo.Version to R.string.version_title),
                                            "target-sdk" to (BackupAppInfo.TargetSDK to R.string.target_sdk_version_title),
                                            "min-sdk" to (BackupAppInfo.MinSDK to R.string.min_sdk_version_title),
                                            "installed-at" to (BackupAppInfo.InstalledAt to R.string.installed_at_title),
                                            "last-update" to (BackupAppInfo.UpdatedAt to R.string.updated_at_title),
                                            "install-source" to (BackupAppInfo.InstallSource to R.string.installer),
                                            "links" to (BackupAppInfo.Links to R.string.links_title),
                                        ).filterNot { (_, spec) -> excludeItems.contains(spec.first) }
                                            .joinToString("\n") { (field, spec) ->
                                                """<label><input type="checkbox" class="field-toggle" data-field="$field" checked> ${getString(spec.second)}</label>"""
                                            }

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
                                            "LOCALISATION_SHOW_OPTIONS" to getString(R.string.show_options),
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
                                            "LOCALISATION_VISIBLE_APP_INFO" to getString(R.string.visible_app_info),
                                            "DISPLAY_FIELD_TOGGLES" to fieldToggles,
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
                                            "ENVIRONMENT_INFO_PLACEHOLDER" to environmentHtml,
                                        )

                                        var finalHtml = template

                                        placeholders
                                            .filterKeys { it != "APP_ITEMS_PLACEHOLDER" }
                                            .forEach { (placeholder, value) ->
                                                finalHtml = finalHtml.replace(
                                                    "<!-- $placeholder -->",
                                                    value
                                                )
                                            }

                                        finalHtml = finalHtml.replace(
                                            "<!-- APP_ITEMS_PLACEHOLDER -->",
                                            placeholders["APP_ITEMS_PLACEHOLDER"]!!
                                        )

                                        if (temporary) {
                                            BackupTempStore.set(format, finalHtml)
                                        } else {
                                            val bytes = finalHtml.toByteArray()
                                            writeBackup(newFile?.uri, bytes)
                                            writeBackup(latestFile?.uri, bytes)
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
                                            if (!isLinksExcluded) append(",\"${getString(R.string.links_title)}\"")
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
                                                // One quoted field holding both links. The commas inside
                                                // must stay within the quotes or the row breaks apart.
                                                if (!isLinksExcluded) append(",\"[Play](https://play.google.com/store/apps/details?id=${app.packageName}), [F-Droid](https://f-droid.org/packages/${app.packageName})\"")
                                                appendLine()
                                            }
                                        }

                                        val csvContent = csvBuilder.toString()

                                        if (temporary) {
                                            BackupTempStore.set(format, csvContent)
                                        } else {
                                            val bytes = csvContent.toByteArray()
                                            writeBackup(newFile?.uri, bytes)
                                            writeBackup(latestFile?.uri, bytes)
                                        }
                                    }

                                    BackupFormat.Markdown -> {
                                        val markdownBuilder = StringBuilder()

                                        val environmentMarkdown =
                                            if (includeSystemInfo) {
                                                backupEnvironmentInfo.toMarkdown()
                                            } else {
                                                ""
                                            }

                                        if (environmentMarkdown.isNotBlank()) {
                                            markdownBuilder.appendLine(environmentMarkdown)
                                            markdownBuilder.appendLine("<br>")
                                        }

                                        markdownBuilder.appendLine("## Installed apps")


                                        markdownBuilder.apply {
                                            append("| ${getString(R.string.name_title)}")
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
                                            append(" |")
                                            appendLine()
                                        }

                                        val linesCount = markdownBuilder.toString().split("|").size - 2
                                        markdownBuilder.apply {
                                            append("|")
                                            repeat(linesCount) {
                                                append(" --- |")
                                            }
                                            appendLine()
                                        }

                                        apps.forEach { app ->
                                            markdownBuilder.apply {
                                                append("| **${app.name}**")
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
                                                if (!isLinksExcluded) append(" | [Play](https://play.google.com/store/apps/details?id=${app.packageName}), [F-Droid](https://f-droid.org/packages/${app.packageName})")

                                                append(" |")
                                                appendLine()
                                            }
                                        }

                                        val markdownContent = markdownBuilder.toString()

                                        if (temporary) {
                                            BackupTempStore.set(format, markdownContent)
                                        } else {
                                            val bytes = markdownContent.toByteArray()
                                            writeBackup(newFile?.uri, bytes)
                                            writeBackup(latestFile?.uri, bytes)
                                        }
                                    }
                                }

                                if (!temporary && newFile == null) {
                                    // Exception, not Error: the catch below records a failed format
                                    // and shows the "backup failed" notification. An Error is not an
                                    // Exception, so throwing one skipped that handling entirely and
                                    // took the process down with it.
                                    throw IOException(getString(R.string.file_create_failed))
                                }

                                results.add(
                                    BackupFormatResult(
                                        format,
                                        newFile,
                                        null
                                    )
                                )
                            } catch (exception: Exception) {
                                results.add(
                                    BackupFormatResult(
                                        format,
                                        null,
                                        exception
                                    )
                                )
                            }
                        }

                        val (successfulResults, unsuccessfulResults) = results.partition { it.isSuccess() }

                        val unsuccessfulMessage =
                            unsuccessfulResults.joinToString("\n") { "${it.format.value}: ${it.exception?.localizedMessage ?: "Unknown error"}" }

                        if (!temporary && successfulResults.isEmpty()) {
                            val endNotification = NotificationCompat.Builder(this, BACKUP_CHANNEL_ID)
                                .setContentTitle(getString(R.string.backup_failed))
                                .setContentText(unsuccessfulMessage)
                                .setSmallIcon(R.drawable.ic_launcher_foreground)
                                .build()

                            val manager = getSystemService(NotificationManager::class.java)
                            manager.notify(getNotificationId(), endNotification)
                        } else {

                            if (temporary) {
                                val endNotification = NotificationCompat.Builder(this, BACKUP_CHANNEL_ID)
                                    .setContentTitle(
                                        resources.getQuantityString(
                                            R.plurals.backup_done_title, appsCount, appsCount, type, "TEMP"
                                        )
                                    )
                                    .setContentText(
                                        getString(
                                            R.string.backup_done_text,
                                            resources.getQuantityString(R.plurals.user_apps_count_text, userAppsCount, userAppsCount),
                                            resources.getQuantityString(R.plurals.system_apps_count_text, systemAppsCount, systemAppsCount)
                                        )
                                    )
                                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                                    .build()

                                val manager = getSystemService(NotificationManager::class.java)
                                manager.notify(getNotificationId(), endNotification)

                                onCompleteCallback?.let {
                                    it(null, true)
                                }
                                onCompleteCallback = null

                                isRunning.value = false
                                return
                            }

                            val backupLimit = Settings.getBackupLimit(this)

                            val firstUri = successfulResults.first().file?.uri

                            if (backupLimit > 0) {
                                val usedFormats = successfulResults.map { it.format }.distinct()
                                val backups = getRawBackupFiles(this)

                                usedFormats.forEach { format ->
                                    val backupsByFormat = backups.filter {
                                        it.name.endsWith(format.fileExtension())
                                    }
                                    val currentCount = backupsByFormat.count()
                                    if (currentCount > backupLimit) {
                                        val filesToDeleteCount = currentCount - backupLimit
                                        val sortedBackups = backupsByFormat.sortedBy { it.lastModified }
                                        sortedBackups.take(filesToDeleteCount).forEach { backupFile ->
                                            backupFile.delete()
                                        }
                                    }
                                }
                            }

                            val mainActivityIntent = Intent(this, MainActivity::class.java).apply {
                                putExtra("uri", firstUri.toString())
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
                                resources.getQuantityString(
                                    R.plurals.backup_done_title, appsCount, appsCount, type,
                                    successfulResults.joinToString(", ") { it.format.value })
                            var successfulText = getString(
                                R.string.backup_done_text,
                                resources.getQuantityString(R.plurals.user_apps_count_text, userAppsCount, userAppsCount),
                                resources.getQuantityString(R.plurals.system_apps_count_text, systemAppsCount, systemAppsCount)
                            )
                            if (unsuccessfulMessage.isNotEmpty()) {
                                successfulText += "\n${getString(R.string.failed)}:\n${unsuccessfulMessage}"
                            }

                            val endNotification = NotificationCompat.Builder(this, BACKUP_CHANNEL_ID)
                                .setContentTitle(successfulTitle)
                                .setContentText(successfulText)
                                .setSmallIcon(R.drawable.ic_launcher_foreground)
                                .setContentIntent(pendingIntent)
                                .build()

                            val manager = getSystemService(NotificationManager::class.java)
                            manager.notify(notificationId, endNotification)

                            if (onCompleteCallback != null && firstUri != null) {
                                onCompleteCallback?.let { it(firstUri, false) }
                                onCompleteCallback = null
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

                    Log.d("BackupService", "end")

                    for (appWidgetId in appWidgetIds) {
                        updateAppWidget(this, appWidgetManager, appWidgetId, showLoading = false)
                    }
                } else {
                    Log.d("BackupService", "failed due no destination")

                    val endNotification = NotificationCompat.Builder(this, BACKUP_CHANNEL_ID)
                        .setContentTitle(getString(R.string.backup_failed))
                        .setContentText(getString(R.string.destination_not_set_notification))
                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                        .build()

                    val manager = getSystemService(NotificationManager::class.java)
                    manager.notify(getNotificationId(), endNotification)
                    promoteForeground?.invoke(1, endNotification)
                }

                isRunning.value = false
            }
        }

        private fun getNotificationId(): Int {
            return (System.currentTimeMillis() and 0xfffffff).toInt()
        }

        private fun Context.writeBackup(uri: Uri?, bytes: ByteArray) {
            uri ?: return
            contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
        }

        private fun drawableToBase64(
            drawable: Drawable,
            maxWidth: Int = 64,
            maxHeight: Int = 64
        ): String {

            val bitmap = if (drawable is BitmapDrawable && drawable.bitmap != null) {
                drawable.bitmap
            } else {
                val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else maxWidth
                val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else maxHeight

                createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                    val canvas = Canvas(this)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                }
            }

            val resizedBitmap = resizeBitmapKeepAspect(bitmap, maxWidth, maxHeight)

            val byteArrayOutputStream = ByteArrayOutputStream()
            resizedBitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)

            val byteArray = byteArrayOutputStream.toByteArray()

            return "data:image/png;base64," +
                    Base64.encodeToString(byteArray, Base64.NO_WRAP)
        }

        private fun resizeBitmapKeepAspect(
            bitmap: Bitmap,
            maxWidth: Int,
            maxHeight: Int
        ): Bitmap {

            val width = bitmap.width
            val height = bitmap.height

            if (width <= maxWidth && height <= maxHeight) {
                return bitmap
            }

            val aspectRatio = width.toFloat() / height.toFloat()

            val (newWidth, newHeight) = if (width > height) {
                val w = maxWidth
                val h = (w / aspectRatio).toInt()
                w to h
            } else {
                val h = maxHeight
                val w = (h * aspectRatio).toInt()
                w to h
            }

            return bitmap.scale(newWidth, newHeight)
        }

        private fun Context.createNotificationChannels() {
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
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(tag, "start: ${intent.toString()}")

        val source = intent?.getStringExtra("source")
        val inputFormat = intent?.getStringExtra("format")
        val temporary = intent?.getBooleanExtra("temporary",false) ?: false

        // One backup at a time. Without this, every start command launched its own coroutine:
        // two triggers landing in the same second derived the same file name, the second
        // createFile returned null, and the backup failed. Claiming the flag synchronously
        // here means a duplicate trigger is ignored rather than racing the one in progress.
        if (!isRunning.compareAndSet(expect = false, update = true)) {
            Log.w(tag, "backup already in progress; ignoring duplicate start")
            return START_NOT_STICKY
        }

        serviceScope.launch {
            try {
                performBackup(this@BackupService, source, inputFormat, temporary, promoteForeground = ::startForeground)
            } finally {
                // Always release the flag, whatever happened, so a failure cannot wedge the
                // service into refusing every future backup.
                isRunning.value = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        Log.d(tag, "destroy")
    }
}