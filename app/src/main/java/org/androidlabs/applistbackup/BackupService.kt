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
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.IBinder
import android.provider.DocumentsContract
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import org.androidlabs.applistbackup.reader.BackupReaderActivity
import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
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
    
    companion object {
        const val SERVICE_CHANNEL_ID = "BackupService"
        const val BACKUP_CHANNEL_ID = "Backup"

        private var onCompleteCallback: ((Uri) -> Unit)? = null

        private const val PREFERENCES_FILE: String = "preferences"
        private const val KEY_BACKUP_URI: String = "backup_uri"

        fun setBackupUri(context: Context, uri: Uri) {
            val sharedPreferences = context.getSharedPreferences(PREFERENCES_FILE, MODE_PRIVATE)
            val editor = sharedPreferences.edit()
            editor.putString(KEY_BACKUP_URI, uri.toString())
            editor.apply()
        }

        fun getBackupUri(context: Context): Uri? {
            val sharedPreferences = context.getSharedPreferences(PREFERENCES_FILE, MODE_PRIVATE)
            val uriString = sharedPreferences.getString(KEY_BACKUP_URI, null)
            return if (uriString != null) Uri.parse(uriString) else null
        }

        fun getBackupFolder(context: Context): DocumentFile? {
            val backupsUri = getBackupUri(context) ?: return null
            return DocumentFile.fromTreeUri(context, backupsUri);
        }

        fun getReadablePathFromUri(uri: Uri?): String {
            if (uri == null) {
                return ""
            }
            val docId = DocumentsContract.getTreeDocumentId(uri)
            val split = docId.split(":")
            val type = split[0]
            val path = split.getOrNull(1) ?: ""
            val decodedPath = URLDecoder.decode(path, StandardCharsets.UTF_8.toString())

            return when (type) {
                "primary" -> "Internal Storage/$decodedPath"
                "home" -> "Home/$decodedPath"
                else -> "$type/$decodedPath"
            }
        }

        fun getLastCreatedFileUri(context: Context): Uri? {
            val backupsUri = getBackupUri(context) ?: return null
            val backupsDir = DocumentFile.fromTreeUri(context, backupsUri)

            if (backupsDir != null && backupsDir.exists() && backupsDir.isDirectory) {
                val files = backupsDir.listFiles()

                if (files.isNotEmpty()) {
                    val sortedFiles = files.sortedByDescending { it.lastModified() }

                    val lastCreatedFile = sortedFiles.firstOrNull()

                    if (lastCreatedFile != null) {
                        return lastCreatedFile.uri
                    }
                }
            }
            return null
        }

        fun getBackupFiles(context: Context): List<BackupFile> {
            val backupsUri = getBackupUri(context) ?: return emptyList()
            val backupsDir = DocumentFile.fromTreeUri(context, backupsUri) ?: return emptyList()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault())
            val titleFormatter = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

            return backupsDir.listFiles()
                .filter { file ->
                    file.name?.let { name ->
                        name.endsWith(".html") && name.startsWith("app-list-backup-")
                    } == true
                }
                .map { file ->
                    val name = file.name ?: return@map null
                    val dateString = name.removePrefix("app-list-backup-").removeSuffix(".html")
                    val date = dateFormat.parse(dateString) ?: Date()
                    val title = titleFormatter.format(date)
                    BackupFile(file.uri, date, title)
                }
                .filterNotNull()
                .sortedByDescending { it.date }
        }

        fun parseDateFromUri(uri: Uri): Date? {
            val pattern = Pattern.compile("app-list-backup-(\\d{4}-\\d{2}-\\d{2}-\\d{2}-\\d{2}-\\d{2})\\.html")
            val matcher = pattern.matcher(uri.toString())

            return if (matcher.find()) {
                val dateString = matcher.group(1)
                val dateFormat = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault())
                dateString?.let {
                    dateFormat.parse(it)
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
        val source: String?
        if (intent != null) {
            source = intent.getStringExtra("source")
        } else {
            source = null
        }
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
                .setContentTitle("Backup started")
                .setContentText("In progress...")
                .setSmallIcon(R.mipmap.ic_launcher)
                .build()

            startForeground(1, notification)

            try {
                val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }

                val dateFormat = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault())
                val outputDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

                val currentDate = Date()
                val currentTime = dateFormat.format(currentDate)

                val template = assets.open("template.html").bufferedReader().use { it.readText() }
                val appItems = StringBuilder()

                var systemAppsCount = 0
                val apps = packageManager.queryIntentActivities(mainIntent, 0)

                apps.forEachIndexed { index, resolveInfo ->
                    val appInfo = resolveInfo.activityInfo.applicationInfo
                    val name = packageManager.getApplicationLabel(appInfo)
                    val packageName = appInfo.packageName
                    val packageInfo = packageManager.getPackageInfo(packageName, 0)
                    val icon = packageManager.getApplicationIcon(appInfo)
                    val isSystem = appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0 ||
                            appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
                    if (isSystem) {
                        systemAppsCount++
                    }

                    appItems.append(
                        """
                <div class="app-item"
                data-install-time="${packageInfo.firstInstallTime}"
                data-update-time="${packageInfo.lastUpdateTime}"
                data-app-name="$name" data-package-name="$packageName"
                data-is-system-app="$isSystem"
                data-is-enabled="${appInfo.enabled}"
                data-default-order="$index">
                    <img src="${drawableToBase64(icon)}" alt="$name">
                    <div class="app-details">
                        <strong class="app-name">$name</strong><br>
                        <strong>Package:</strong> $packageName<br>
                        <strong>System:</strong> ${isSystem}<br>
                        <strong>Enabled:</strong> ${appInfo.enabled}<br>
                        <strong>Version:</strong> ${packageInfo.versionName} (${packageInfo.longVersionCode})<br>
                        <strong>Min SDK version:</strong> ${appInfo.minSdkVersion}<br>
                        <strong>Installed at:</strong> ${outputDateFormat.format(Date(packageInfo.firstInstallTime))}<br>
                        <strong>Last update at:</strong> ${outputDateFormat.format(Date(packageInfo.lastUpdateTime))}<br>
                        <strong>Links</strong> (is working only for published apps):<br>
                        <a target="_blank" rel="noopener noreferrer" href="https://play.google.com/store/apps/details?id=$packageName">Play Market</a> | 
                        <a target="_blank" rel="noopener noreferrer" href="https://f-droid.org/packages/$packageName">F-Droid</a>
                    </div>
                </div>
            """.trimIndent()
                    )
                }

                val fileName = "app-list-backup-$currentTime.html"
                var finalHtml = template.replace("<!-- APP_ITEMS_PLACEHOLDER -->", appItems.toString())
                finalHtml = finalHtml.replace("<!-- BACKUP_TIME_PLACEHOLDER -->", outputDateFormat.format(currentDate))

                val newFile = backupsDir.createFile("text/html", fileName)

                if (newFile != null) {
                    contentResolver.openOutputStream(newFile.uri)?.use { outputStream ->
                        outputStream.write(finalHtml.toByteArray())
                    }
                } else {
                    throw Error("Unable to create backup file.")
                }

                val openFileIntent = Intent(this, BackupReaderActivity::class.java).apply {
                    putExtra("uri", newFile.uri.toString())
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }

                val pendingIntent = PendingIntent.getActivity(this, 0, openFileIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

                val type = if (source != null && source == "tasker") "Automatic" else "Manual"
                val successfulTitle = "Backup done for ${apps.count()} apps ($type)"
                val successfulText = "${apps.count() - systemAppsCount} users apps and $systemAppsCount system apps."

                val endNotification = NotificationCompat.Builder(this, BACKUP_CHANNEL_ID)
                    .setContentTitle(successfulTitle)
                    .setContentText(successfulText)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentIntent(pendingIntent)
                    .build()

                val manager = getSystemService(NotificationManager::class.java)
                manager.notify(getNotificationId(), endNotification)

                if (onCompleteCallback != null) {
                    onCompleteCallback?.let { it(newFile.uri) }
                    onCompleteCallback = null
                }
            } catch (error: Error) {
                val endNotification = NotificationCompat.Builder(this, BACKUP_CHANNEL_ID)
                    .setContentTitle("Backup failed")
                    .setContentText(error.localizedMessage)
                    .setSmallIcon(R.mipmap.ic_launcher)
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
                .setContentTitle("Backup failed")
                .setContentText("You need to setup destination folder at first.")
                .setSmallIcon(R.mipmap.ic_launcher)
                .build()

            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(getNotificationId(), endNotification)
        }

        stopForeground(STOP_FOREGROUND_REMOVE)

        return START_STICKY
    }

    private fun getNotificationId(): Int {
        return (System.currentTimeMillis() and 0xfffffff).toInt()
    }

    private fun drawableToBase64(drawable: Drawable): String {
        val bitmap = if (drawable is BitmapDrawable) {
            drawable.bitmap
        } else {
            Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888).apply {
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
        Log.d(tag, "destroy")
    }
}