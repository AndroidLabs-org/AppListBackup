package org.androidlabs.applistbackup.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.androidlabs.applistbackup.data.BackupFormat

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val _backupUri: MutableLiveData<Uri?> = MutableLiveData(loadBackupUri())
    private val _backupFormat: MutableLiveData<Set<BackupFormat>> =
        MutableLiveData(loadBackupFormats())
    private val _backupLimit: MutableLiveData<Int> =
        MutableLiveData(loadBackupLimit())

    val backupUri: LiveData<Uri?> get() = _backupUri
    val backupFormats: LiveData<Set<BackupFormat>> get() = _backupFormat
    val backupLimit: LiveData<Int> get() = _backupLimit

    private fun loadBackupUri(): Uri? {
        return Settings.getBackupUri(getApplication())
    }

    private fun loadBackupFormats(): Set<BackupFormat> {
        return Settings.getBackupFormats(getApplication())
    }

    private fun loadBackupLimit(): Int {
        return Settings.getBackupLimit(getApplication())
    }

    fun refresh() {
        _backupUri.postValue(loadBackupUri())
        _backupFormat.postValue(loadBackupFormats())
        _backupLimit.postValue(loadBackupLimit())
    }

    fun saveBackupUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            Settings.setBackupUri(getApplication(), uri)
            _backupUri.postValue(uri)
        }
    }

    fun saveBackupFormats(format: Set<BackupFormat>) {
        viewModelScope.launch(Dispatchers.IO) {
            Settings.setBackupFormats(getApplication(), format)
            _backupFormat.postValue(format)
        }
    }

    fun saveBackupLimit(value: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            Settings.setBackupLimit(getApplication(), value)
            _backupLimit.postValue(value)
        }
    }
}