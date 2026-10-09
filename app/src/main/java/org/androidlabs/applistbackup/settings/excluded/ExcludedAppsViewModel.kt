package org.androidlabs.applistbackup.settings.excluded

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.androidlabs.applistbackup.data.BackupAppInfo
import org.androidlabs.applistbackup.data.SortBy
import org.androidlabs.applistbackup.data.SortOrder
import org.androidlabs.applistbackup.settings.Settings

class ExcludedAppsViewModel(application: Application) : AndroidViewModel(application) {
    private val _selectedPackages =
        MutableStateFlow<Set<String>>(emptySet())

    val selectedPackages = _selectedPackages.asStateFlow()

    fun togglePackage(packageName: String) {
        _selectedPackages.update { current ->
            if (packageName in current) {
                current - packageName
            } else {
                current + packageName
            }
        }
    }

    init {
        viewModelScope.launch {
            val savedPackages = Settings.getBackupExcludedPackages(getApplication())
            _selectedPackages.value = savedPackages.toSet()
        }
    }

    fun clearAll() {
        _selectedPackages.value = emptySet()
    }

    fun save() {
        Settings.setBackupExcludedPackages(getApplication(), selectedPackages.value)
    }
}