package org.androidlabs.applistbackup

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

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