package org.androidlabs.applistbackup

import android.app.Application
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.FileObserver
import androidx.core.app.NotificationManagerCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import org.androidlabs.applistbackup.settings.Settings
import java.io.File

class BackupViewModel(application: Application) : AndroidViewModel(application) {

    private val notificationManagerCompat = NotificationManagerCompat.from(application)

    val notificationEnabled: MutableLiveData<Boolean> = MutableLiveData(checkNotificationEnabled())

    val backupUri: LiveData<Uri?> get() = _backupUri

    private val _backupUri: MutableLiveData<Uri?> = MutableLiveData(loadBackupUri())
    private val _backupFiles = MutableLiveData<List<BackupFile>>(emptyList())
    val backupFiles: LiveData<List<BackupFile>> = _backupFiles

    private var backupUriListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var fileObserver: FileObserver? = null

    init {
        initializeFileObserver()

        updateBackupFiles()

        backupUriListener = Settings.observeBackupUri(getApplication(), {
            initializeFileObserver()
            updateBackupFiles()
        })
    }


    private fun checkNotificationEnabled(): Boolean {
        return notificationManagerCompat.areNotificationsEnabled()
    }

    fun refreshNotificationStatus() {
        notificationEnabled.postValue(checkNotificationEnabled())
    }

    private fun loadBackupUri(): Uri? {
        return Settings.getBackupUri(getApplication())
    }

    fun refreshBackupUri() {
        _backupUri.postValue(loadBackupUri())
    }

    private fun initializeFileObserver() {
        val backupsUri = Settings.getBackupUri(getApplication()) ?: return

        val backupFolderPath =
            DocumentFile.fromTreeUri(getApplication(), backupsUri)?.uri?.path ?: return

        fileObserver?.stopWatching()

        fileObserver = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val backupFolder = File(backupFolderPath)
            object : FileObserver(backupFolder, CREATE or DELETE or MODIFY) {
                override fun onEvent(event: Int, path: String?) {
                    if (path != null) {
                        updateBackupFiles()
                    }
                }
            }
        } else {
            @Suppress("DEPRECATION")
            object : FileObserver(backupFolderPath) {
                override fun onEvent(event: Int, path: String?) {
                    if (event == CREATE || event == DELETE || event == MODIFY) {
                        if (path != null) {
                            updateBackupFiles()
                        }
                    }
                }
            }
        }

        fileObserver?.startWatching()
    }

    fun updateBackupFiles() {
        val files = BackupService.getBackupFiles(getApplication())
        _backupFiles.value = files
    }

    override fun onCleared() {
        super.onCleared()
        fileObserver?.stopWatching()
        backupUriListener?.let {
            Settings.unregisterListener(getApplication(), it)
        }
    }
}