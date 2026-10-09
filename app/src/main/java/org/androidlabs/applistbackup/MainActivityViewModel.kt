package org.androidlabs.applistbackup

import android.app.Application
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.androidlabs.applistbackup.data.BackupFile
import org.androidlabs.applistbackup.data.BackupFormat
import org.androidlabs.applistbackup.data.BackupItem
import org.androidlabs.applistbackup.settings.Settings

class MainActivityViewModel(application: Application) : AndroidViewModel(application) {
    private val _uri = MutableStateFlow<Uri?>(null)
    private val _shouldNavigateToBrowse = MutableStateFlow(false)

    val uri = _uri.asStateFlow()
    val shouldNavigateToBrowse = _shouldNavigateToBrowse.asStateFlow()

    private val _backupFiles = MutableStateFlow<List<BackupItem>>(emptyList())
    val backupFiles: StateFlow<List<BackupItem>> = _backupFiles.asStateFlow()

    private val _isBackupRunning = MutableStateFlow(false)
    val isBackupRunning: StateFlow<Boolean> = _isBackupRunning.asStateFlow()
    private val _backupUri: MutableLiveData<Uri?> = MutableLiveData(loadBackupUri())
    val backupUri: LiveData<Uri?> get() = _backupUri
    private var backupSettingsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    private val _backupDeletedEvent = MutableSharedFlow<Unit>()
    val backupDeletedEvent = _backupDeletedEvent.asSharedFlow()

    private val _selectedTempFormat = MutableStateFlow<BackupFormat?>(null)
    val selectedTempFormat = _selectedTempFormat.asStateFlow()

    fun setSelectedTempFormat(format: BackupFormat?) {
        _selectedTempFormat.value = format
    }
    fun setUri(uri: Uri?) {
        _uri.value = uri
    }

    fun navigateToBrowse() {
        _shouldNavigateToBrowse.value = true
    }

    fun onNavigationHandled() {
        _shouldNavigateToBrowse.value = false
    }

    init {
        viewModelScope.launch {
            updateBackupFiles()
        }

        backupSettingsListener =
            Settings.observeBackupUri(getApplication()) {
                refreshBackupUri()
                updateBackupFiles()
            }

        viewModelScope.launch {
            BackupService.isRunning.drop(1).collect { state ->
                _isBackupRunning.value = state
                if (!state) {
                    updateBackupFiles()
                }
            }
        }

    }

    fun refreshBackupUri() {
        _backupUri.postValue(loadBackupUri())
    }

    private fun loadBackupUri(): Uri? {
        return Settings.getBackupUri(getApplication())
    }
    private fun updateBackupFiles() {
        val files = BackupService.getBackupFiles(getApplication())
        _backupFiles.value =
            files.map {
                BackupItem.File(it)
            }
    }

    fun refreshBackupFiles() {
        viewModelScope.launch {
            updateBackupFiles()
            val lastUri = BackupService.getLastCreatedFileUri(getApplication())
            lastUri?.let {
                    setUri(it)
            }
            _backupDeletedEvent.emit(Unit)
        }
    }

    override fun onCleared() {
        backupSettingsListener?.let {
            Settings.unregisterListener(getApplication(), it)
        }
    }

    private val _activeScreen = MutableStateFlow(ActiveScreen.BACKUP)
    val activeScreen = _activeScreen.asStateFlow()

    fun setActiveScreen(screen: ActiveScreen) {
        if (screen!= ActiveScreen.READER)
            BackupTempStore.clear()
        _activeScreen.value = screen
    }
    enum class ActiveScreen {
        BACKUP,
        READER,
        SETTINGS
    }
}