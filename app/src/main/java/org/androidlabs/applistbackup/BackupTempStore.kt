package org.androidlabs.applistbackup

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.androidlabs.applistbackup.data.BackupFormat

object BackupTempStore {
    private val _data = MutableStateFlow<Map<BackupFormat, String>>(emptyMap())
    val data = _data.asStateFlow()

    fun set(format: BackupFormat, value: String) {
        _data.value = _data.value.toMutableMap().apply {
            put(format, value)
        }
    }

    fun get(format: BackupFormat): String? {
        return _data.value[format]
    }

    fun clear() {
        _data.value = emptyMap()
    }
}