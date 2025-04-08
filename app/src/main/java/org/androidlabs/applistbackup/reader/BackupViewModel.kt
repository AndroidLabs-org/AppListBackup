package org.androidlabs.applistbackup.reader

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.net.toFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.androidlabs.applistbackup.BackupFile
import org.androidlabs.applistbackup.BackupService
import org.androidlabs.applistbackup.settings.Settings
import org.androidlabs.applistbackup.utils.Utils.isTV

class BackupViewModel(application: Application) : AndroidViewModel(application) {
    private val tag: String = "BackupViewModel"

    private val _uri = MutableStateFlow<Uri?>(null)
    val uri: StateFlow<Uri?> = _uri.asStateFlow()

    private val _backupFiles = MutableStateFlow<List<BackupFile>>(emptyList())
    val backupFiles: StateFlow<List<BackupFile>> = _backupFiles.asStateFlow()

    private val _installedPackages = MutableStateFlow<List<String>>(emptyList())
    val installedPackages: StateFlow<List<String>> = _installedPackages.asStateFlow()

    private var backupSettingsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    private val viewModelSupervisorJob = SupervisorJob()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            initializeViewModel()
        }

        viewModelScope.launch {
            BackupService.isRunning.collect { state ->
                if (!state) {
                    updateBackupFiles()
                }
            }
        }
    }

    private suspend fun initializeViewModel() {
        withContext(Dispatchers.IO) {
            loadInstalledPackages()

            withContext(Dispatchers.Main) {
                backupSettingsListener = Settings.observeBackupUri(
                    context = getApplication(),
                    onChangeBackupUri = {
                        viewModelScope.launch(Dispatchers.IO) {
                            updateBackupFiles()
                            val lastUri = BackupService.getLastCreatedFileUri(getApplication())
                            _uri.value = lastUri
                        }
                    }
                )
            }

            updateBackupFiles()
        }
    }

    private suspend fun loadInstalledPackages() {
        withContext(Dispatchers.IO) {
            try {
                val packageManager = getApplication<Application>().packageManager
                val packages = packageManager.getInstalledPackages(PackageManager.GET_META_DATA)
                val packageNames = packages.map { it.packageName }
                _installedPackages.value = packageNames
            } catch (e: Exception) {
                Log.e(tag, e.toString())
                _installedPackages.value = emptyList()
            }
        }
    }

    fun setUri(context: Context, newUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val finalUri = if (isTV(context)) {
                val isFileProvider = newUri.scheme == ContentResolver.SCHEME_CONTENT
                if (!isFileProvider) {
                    try {
                        FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.provider",
                            newUri.toFile()
                        )
                    } catch (e: Exception) {
                        Log.e(tag, e.toString())
                        newUri
                    }
                } else {
                    newUri
                }
            } else {
                newUri
            }

            _uri.value = finalUri
        }
    }

    private fun updateBackupFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val files = BackupService.getBackupFiles(getApplication())
                _backupFiles.value = files
            } catch (e: Exception) {
                Log.e(tag, e.toString())
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelSupervisorJob.cancelChildren()
        backupSettingsListener?.let {
            Settings.unregisterListener(getApplication(), it)
        }
    }
}