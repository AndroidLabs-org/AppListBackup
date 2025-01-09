package org.androidlabs.applistbackup

import android.app.Application
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.FileObserver
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.androidlabs.applistbackup.settings.Settings
import java.io.File

class MainActivityViewModel(application: Application) : AndroidViewModel(application) {
    private val _uri = MutableStateFlow<Uri?>(null)
    private val _shouldNavigateToBrowse = MutableStateFlow(false)

    val uri = _uri.asStateFlow()
    val shouldNavigateToBrowse = _shouldNavigateToBrowse.asStateFlow()

    fun setUri(uri: Uri?) {
        _uri.value = uri
    }

    fun navigateToBrowse() {
        _shouldNavigateToBrowse.value = true
    }

    fun onNavigationHandled() {
        _shouldNavigateToBrowse.value = false
    }
}