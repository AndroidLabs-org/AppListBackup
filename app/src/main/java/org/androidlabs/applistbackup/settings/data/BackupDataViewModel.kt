package org.androidlabs.applistbackup.settings.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.androidlabs.applistbackup.data.BackupAppInfo
import org.androidlabs.applistbackup.settings.Settings

class BackupDataViewModel(application: Application) : AndroidViewModel(application) {
    private val _backupExcludeData: MutableLiveData<List<BackupAppInfo>> =
        MutableLiveData(loadBackupExcludeData())

    val backupExcludeData: LiveData<List<BackupAppInfo>> get() = _backupExcludeData

    private fun loadBackupExcludeData(): List<BackupAppInfo> {
        return Settings.getBackupExcludeData(getApplication())
    }

    fun refresh() {
        _backupExcludeData.postValue(loadBackupExcludeData())
    }

    fun saveBackupExcludeData(list: List<BackupAppInfo>) {
        viewModelScope.launch(Dispatchers.IO) {
            Settings.setBackupExcludeData(getApplication(), list)
            _backupExcludeData.postValue(list)
        }
    }

    fun selectAll() {
        val newList = emptyList<BackupAppInfo>()
        viewModelScope.launch(Dispatchers.IO) {
            Settings.setBackupExcludeData(getApplication(), newList)
            _backupExcludeData.postValue(newList)
        }
    }

    fun deselectAll() {
        val newList = BackupAppInfo.entries
        viewModelScope.launch(Dispatchers.IO) {
            Settings.setBackupExcludeData(getApplication(), newList)
            _backupExcludeData.postValue(newList)
        }
    }
}