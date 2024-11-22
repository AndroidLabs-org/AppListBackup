package org.androidlabs.applistbackup.reader

import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.FileObserver
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import org.androidlabs.applistbackup.BackupFile
import org.androidlabs.applistbackup.BackupService
import java.io.File

class BackupViewModel(application: Application) : AndroidViewModel(application) {
    private val _uri = MutableLiveData<Uri?>(null)
    val uri: LiveData<Uri?> = _uri

    private val _backupFiles = MutableLiveData<List<BackupFile>>(emptyList())
    val backupFiles: LiveData<List<BackupFile>> = _backupFiles

    private val _installedPackages = MutableLiveData<List<String>>(emptyList())
    val installedPackages: LiveData<List<String>> = _installedPackages;

    private var fileObserver: FileObserver? = null

    init {
        initializeFileObserver()
        val packageManager = application.packageManager
        val installedPackages = packageManager.getInstalledPackages(PackageManager.GET_META_DATA)
        val packageNames = installedPackages.map { it.packageName }
        _installedPackages.value = packageNames
    }

    fun setUri(newUri: Uri) {
        _uri.value = newUri
        initializeFileObserver()
        updateBackupFiles()
    }

    private fun initializeFileObserver() {
        val backupsUri = BackupService.getBackupUri(getApplication()) ?: return

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
    }
}