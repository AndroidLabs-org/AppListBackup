package org.androidlabs.applistbackup.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val _backupUri: MutableLiveData<Uri?> = MutableLiveData(loadBackupUri())

    val backupUri: LiveData<Uri?> get() = _backupUri

    private fun loadBackupUri(): Uri? {
        return Settings.getBackupUri(getApplication())
    }

    fun refreshBackupUri() {
        _backupUri.postValue(loadBackupUri())
    }

    fun saveBackupUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            Settings.setBackupUri(getApplication(), uri)
            _backupUri.postValue(uri)
        }
    }
}