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
    private val _backupFormat: MutableLiveData<BackupFormat> = MutableLiveData(loadBackupFormat())

    val backupUri: LiveData<Uri?> get() = _backupUri
    val backupFormat: LiveData<BackupFormat> get() = _backupFormat

    private fun loadBackupUri(): Uri? {
        return Settings.getBackupUri(getApplication())
    }

    private fun loadBackupFormat(): BackupFormat {
        return Settings.getBackupFormat(getApplication())
    }

    fun refresh() {
        _backupUri.postValue(loadBackupUri())
        _backupFormat.postValue(loadBackupFormat())
    }

    fun saveBackupUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            Settings.setBackupUri(getApplication(), uri)
            _backupUri.postValue(uri)
        }
    }

    fun saveBackupFormat(format: BackupFormat) {
        viewModelScope.launch(Dispatchers.IO) {
            Settings.setBackupFormat(getApplication(), format)
            _backupFormat.postValue(format)
        }
    }
}