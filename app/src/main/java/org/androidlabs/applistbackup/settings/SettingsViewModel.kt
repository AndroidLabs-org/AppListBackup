package org.androidlabs.applistbackup.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.androidlabs.applistbackup.data.BackupApp
import org.androidlabs.applistbackup.data.BackupFormat

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val _backupUri: MutableLiveData<Uri?> = MutableLiveData(loadBackupUri())
    private val _backupLimit: MutableLiveData<Int> =
        MutableLiveData(loadBackupLimit())

    val backupUri: LiveData<Uri?> get() = _backupUri
    val backupLimit: LiveData<Int> get() = _backupLimit

    private val _includeSystemInfo = MutableStateFlow(loadIncludeSystemInfo())
    val includeSystemInfo = _includeSystemInfo.asStateFlow()

    private val _backupFormats = MutableStateFlow(loadBackupFormats())
    val backupFormats = _backupFormats.asStateFlow()
    private val _backupApps: MutableLiveData<Set<BackupApp>> =
        MutableLiveData(loadBackupApps())

    val backupApps: LiveData<Set<BackupApp>> get() = _backupApps


    private fun loadIncludeSystemInfo(): Boolean {
        return Settings.getIncludeSystemInfo(getApplication())
    }

    fun saveIncludeSystemInfo(value: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            Settings.setIncludeSystemInfo(getApplication(), value)
            _includeSystemInfo.value = value
        }
    }


    private fun loadBackupUri(): Uri? {
        return Settings.getBackupUri(getApplication())
    }

    private fun loadBackupFormats(): Set<BackupFormat> {
        return Settings.getBackupFormats(getApplication())
    }

    private fun loadBackupLimit(): Int {
        return Settings.getBackupLimit(getApplication())
    }


    private fun loadBackupApps(): Set<BackupApp> {
        return Settings.getBackupApps(getApplication())
    }

    fun saveBackupApps(apps: Set<BackupApp>) {
        viewModelScope.launch(Dispatchers.IO) {
            Settings.setBackupApps(getApplication(), apps)
            _backupApps.postValue(apps)
        }
    }
    fun refresh() {
        _backupUri.postValue(loadBackupUri())
        _backupFormats.value = Settings.getBackupFormats(getApplication())
        _backupLimit.postValue(loadBackupLimit())
        _includeSystemInfo.value = loadIncludeSystemInfo()
    }

    fun saveBackupUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            Settings.setBackupUri(getApplication(), uri)
            _backupUri.postValue(uri)
        }
    }


    fun saveBackupLimit(value: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            Settings.setBackupLimit(getApplication(), value)
            _backupLimit.postValue(value)
        }
    }

    private val _createLatestBackupEnabled = MutableStateFlow(loadCreateLatestBackupEnabled())
    val createLatestBackupEnabled = _createLatestBackupEnabled.asStateFlow()

    private fun loadCreateLatestBackupEnabled(): Boolean {
        return Settings.getCreateLatestBackup(getApplication())
    }

    fun saveCreateLatestBackupEnabled(value: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            Settings.setCreateLatestBackup(getApplication(), value)
            _createLatestBackupEnabled.value = value
        }
    }
}