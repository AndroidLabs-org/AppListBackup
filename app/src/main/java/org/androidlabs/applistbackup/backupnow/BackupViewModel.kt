package org.androidlabs.applistbackup.backupnow

import android.app.Application
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.androidlabs.applistbackup.BackupFile
import org.androidlabs.applistbackup.BackupService
import org.androidlabs.applistbackup.settings.Settings

class BackupViewModel(application: Application) : AndroidViewModel(application) {

    private val notificationManagerCompat = NotificationManagerCompat.from(application)

    val notificationEnabled: MutableLiveData<Boolean> = MutableLiveData(checkNotificationEnabled())

    val backupUri: LiveData<Uri?> get() = _backupUri

    private val _backupUri: MutableLiveData<Uri?> = MutableLiveData(loadBackupUri())
    private val _backupFiles = MutableLiveData<List<BackupFile>>(emptyList())
    val backupFiles: LiveData<List<BackupFile>> = _backupFiles

    private val _isBackupRunning = MutableStateFlow(false)
    val isBackupRunning: StateFlow<Boolean> = _isBackupRunning.asStateFlow()

    private var backupSettingsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var pollingJob: Job? = null

    init {
        initializeFileObserver()

        updateBackupFiles()

        backupSettingsListener =
            Settings.observeBackupUri(getApplication(), ::refreshBackups)

        viewModelScope.launch {
            BackupService.isRunning.collect { state ->
                _isBackupRunning.value = state
            }
        }
    }

    private fun refreshBackups() {
        initializeFileObserver()
        updateBackupFiles()
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