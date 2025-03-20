package org.androidlabs.applistbackup.reader

import android.app.Application
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.androidlabs.applistbackup.BackupFile
import org.androidlabs.applistbackup.BackupService
import org.androidlabs.applistbackup.settings.Settings

class BackupViewModel(application: Application) : AndroidViewModel(application) {
    private val _uri = MutableLiveData<Uri?>(null)
    val uri: LiveData<Uri?> = _uri

    private val _backupFiles = MutableLiveData<List<BackupFile>>(emptyList())
    val backupFiles: LiveData<List<BackupFile>> = _backupFiles

    private val _installedPackages = MutableLiveData<List<String>>(emptyList())
    val installedPackages: LiveData<List<String>> = _installedPackages

    private var backupSettingsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var pollingJob: Job? = null

    init {
        refreshBackups()
        val packageManager = application.packageManager
        val installedPackages = packageManager.getInstalledPackages(PackageManager.GET_META_DATA)
        val packageNames = installedPackages.map { it.packageName }
        _installedPackages.value = packageNames

        backupSettingsListener = Settings.observeBackupUri(
            context = getApplication(),
            onChangeBackupUri = {
                refreshBackups()
                _uri.value = BackupService.getLastCreatedFileUri(getApplication())
            }
        )
    }

    fun setUri(newUri: Uri) {
        _uri.value = newUri
    }

    private fun refreshBackups() {
        initializeFileObserver()
        updateBackupFiles()
    }

    private fun initializeFileObserver() {
        pollingJob?.cancel()

        pollingJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                val files = BackupService.getBackupFiles(getApplication())
                if (files.count() != (_backupFiles.value?.count() ?: 0)) {
                    viewModelScope.launch {
                        _backupFiles.value = files
                    }
                }
                delay(2000)
            }
        }
    }

    private fun updateBackupFiles() {
        val files = BackupService.getBackupFiles(getApplication())
        _backupFiles.value = files
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
        backupSettingsListener?.let {
            Settings.unregisterListener(getApplication(), it)
        }
    }
}