package org.androidlabs.applistbackup.reader

import android.app.Application
import android.net.Uri
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

    private var fileObserver: FileObserver? = null

    init {
        initializeFileObserver()
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

        val backupFolder = File(backupFolderPath)

        fileObserver?.stopWatching()

        fileObserver = object : FileObserver(backupFolder, CREATE or DELETE or MODIFY) {
            override fun onEvent(event: Int, path: String?) {
                if (path != null) {
                    updateBackupFiles()
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