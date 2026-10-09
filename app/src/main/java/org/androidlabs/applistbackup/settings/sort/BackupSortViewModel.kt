package org.androidlabs.applistbackup.settings.sort

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.androidlabs.applistbackup.data.BackupAppInfo
import org.androidlabs.applistbackup.data.SortBy
import org.androidlabs.applistbackup.data.SortOrder
import org.androidlabs.applistbackup.settings.Settings

class BackupSortViewModel(application: Application) : AndroidViewModel(application) {
    private val _sortBy =
        MutableStateFlow(Settings.getBackupSortBy(getApplication()))

    val sortBy = _sortBy.asStateFlow()

    private val _sortOrder =
        MutableStateFlow(Settings.getBackupSortOrder(getApplication()))

    val sortOrder = _sortOrder.asStateFlow()

    fun setSortBy(value: SortBy) {
        Settings.setBackupSortBy(getApplication(), value)
        _sortBy.value = value
    }

    fun setSortOrder(value: SortOrder) {
        Settings.setBackupSortOrder(getApplication(), value)
        _sortOrder.value = value
    }
}