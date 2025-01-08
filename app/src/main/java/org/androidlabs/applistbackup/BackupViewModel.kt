package org.androidlabs.applistbackup

import android.app.Application
import android.net.Uri
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.androidlabs.applistbackup.settings.Settings

class BackupViewModel(application: Application) : AndroidViewModel(application) {

    private val notificationManagerCompat = NotificationManagerCompat.from(application)

    val notificationEnabled: MutableLiveData<Boolean> = MutableLiveData(checkNotificationEnabled())
    private val _backupUri: MutableLiveData<Uri?> = MutableLiveData(loadBackupUri())
    private val _isLoading: MutableLiveData<Boolean> = MutableLiveData(false)

    val backupUri: LiveData<Uri?> get() = _backupUri
    val isLoading: LiveData<Boolean> get() = _isLoading

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

    fun setLoading(value: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.postValue(value)
        }
    }
}